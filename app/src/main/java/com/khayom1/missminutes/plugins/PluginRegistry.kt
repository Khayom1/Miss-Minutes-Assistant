package com.khayom1.missminutes.plugins

import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.ExecutionException
import java.util.concurrent.Future
import java.util.concurrent.RejectedExecutionException
import java.util.concurrent.ThreadFactory
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException

/**
 * Closed-world plugin dispatcher. Only handlers compiled into the app can run;
 * manifests are declarative schemas and never load or execute third-party code.
 */
class PluginRegistry(
    handlers: Collection<PluginActionHandler>,
    private val consentStore: PluginConsentStore,
    private val auditLog: PluginAuditLog,
    private val clockMillis: () -> Long = System::currentTimeMillis,
    private val globalCallsPerMinute: Int = 12,
    private val maxInputCharacters: Int = 2_000,
    private val maxOutputCharacters: Int = 4_000,
    private val accessChecker: PluginAccessChecker = PluginAccessChecker { _, _ ->
        PluginAccessDecision.Unavailable("Runtime access checker is not configured.")
    }
) {
    private val handlersById = handlers.associateBy { it.actionId }
    private val manifests = linkedMapOf<String, PluginManifest>()
    private val globalCallTimes = ArrayDeque<Long>()
    private val pluginCallTimes = mutableMapOf<String, ArrayDeque<Long>>()

    init {
        require(handlersById.size == handlers.size) { "Duplicate compiled plugin action handler." }
        require(globalCallsPerMinute > 0)
        require(maxInputCharacters > 0)
        require(maxOutputCharacters > 0)
    }

    @Synchronized
    fun install(manifest: PluginManifest): Boolean {
        validateManifest(manifest)
        if (manifests.containsKey(manifest.id)) return false
        manifests[manifest.id] = manifest
        return true
    }

    /**
     * Revocation is a security-reducing operation and remains available even if
     * the plugin is currently absent from the in-memory registry.
     */
    @Synchronized
    fun revokeConsent(pluginId: String): Boolean {
        if (!PLUGIN_ID.matches(pluginId)) return false
        val mutableStore = consentStore as? MutablePluginConsentStore ?: return false
        return try {
            mutableStore.setGranted(pluginId, false)
            record(PluginAuditEntry(clockMillis(), pluginId, "consent", "consent_revoked"))
            true
        } catch (_: Exception) {
            false
        }
    }

    /** Removes the manifest only after the persisted grant has been revoked. */
    @Synchronized
    fun uninstall(pluginId: String): Boolean {
        if (!manifests.containsKey(pluginId)) return false
        val mutableStore = consentStore as? MutablePluginConsentStore ?: return false
        return try {
            mutableStore.setGranted(pluginId, false)
            manifests.remove(pluginId)
            record(PluginAuditEntry(clockMillis(), pluginId, "uninstall", "uninstalled"))
            true
        } catch (_: Exception) {
            false
        }
    }

    @Synchronized
    fun installedPlugins(): List<PluginManifest> = manifests.values.toList()

    /** Connect is a one-time user action. The durable grant is reused for later turns. */
    @Synchronized
    fun connect(pluginId: String): Boolean {
        val manifest = manifests[pluginId] ?: return false
        val mutableStore = consentStore as? MutablePluginConsentStore ?: return false
        if (mutableStore.isGranted(manifest.id)) return true
        val now = clockMillis()
        if (!record(PluginAuditEntry(now, manifest.id, "connection", "connect_requested"))) return false
        return try {
            mutableStore.setGranted(manifest.id, true)
            if (record(PluginAuditEntry(now, manifest.id, "connection", "connected"))) {
                true
            } else {
                mutableStore.setGranted(manifest.id, false)
                false
            }
        } catch (_: Exception) {
            record(PluginAuditEntry(now, manifest.id, "connection", "connect_failed"))
            false
        }
    }

    @Synchronized
    fun isConnected(pluginId: String): Boolean =
        manifests.containsKey(pluginId) && consentStore.isGranted(pluginId)

    @Synchronized
    fun connectedPlugins(): List<PluginManifest> =
        manifests.values.filter { consentStore.isGranted(it.id) }.sortedBy { it.id }

    /**
     * Full local catalog for the assistant and settings UI. It describes only
     * actions backed by compiled handlers; imported metadata cannot create code.
     */
    @Synchronized
    fun toolCatalog(): List<PluginToolDescriptor> = manifests.values
        .sortedBy { it.id }
        .flatMap { manifest ->
            manifest.actions.sortedBy { it.id }.mapNotNull { action ->
                val handler = handlersById[action.id]
                    ?.takeIf { it.capability == action.capability }
                    ?: return@mapNotNull null
                val access = runCatching { accessChecker.inspect(manifest.id, action) }
                    .getOrElse { PluginAccessDecision.Unavailable("Access could not be verified safely.") }
                PluginToolDescriptor(
                    toolName = stableToolName(manifest.id, action.id),
                    pluginId = manifest.id,
                    actionId = action.id,
                    pluginName = manifest.displayName,
                    description = manifest.description,
                    capability = handler.capability,
                    arguments = handler.argumentSchema.toMap(),
                    requiresConfirmation = action.requiresConfirmation || handler.requiresConfirmation || action.capability.requiresConfirmation,
                    connected = consentStore.isGranted(manifest.id),
                    access = access,
                    sideEffect = action.requiresConfirmation || handler.requiresConfirmation ||
                        action.capability.requiresConfirmation || handler.sideEffect
                )
            }
        }

    /** Connected tools are discovered automatically; callers do not pick plugins per message. */
    @Synchronized
    fun connectedTools(): List<PluginToolDescriptor> =
        toolCatalog().filter { it.connected }

    /** Tools ready for immediate execution with currently available access. */
    @Synchronized
    fun readyTools(): List<PluginToolDescriptor> =
        connectedTools().filter { it.access is PluginAccessDecision.Granted }

    @Synchronized
    fun execute(request: PluginExecutionRequest): PluginExecutionResult {
        val now = clockMillis()
        val manifest = manifests[request.pluginId]
            ?: return denied(
                now, request, null, "Plugin is not installed.",
                PluginExecutionStatus.PLUGIN_NOT_INSTALLED
            )
        val action = manifest.actions.firstOrNull { it.id == request.actionId }
            ?: return denied(
                now, request, null, "Plugin action is not allowlisted.",
                PluginExecutionStatus.ACTION_NOT_ALLOWED
            )
        val handler = handlersById[action.id]
            ?: return denied(
                now, request, action, "Plugin action handler is unavailable.",
                PluginExecutionStatus.HANDLER_UNAVAILABLE
            )

        if (!consentStore.isGranted(manifest.id)) {
            return denied(
                now, request, action, "Plugin is installed but not connected. Connect it once in Plugin settings.",
                PluginExecutionStatus.PLUGIN_NOT_CONNECTED
            )
        }

        val access = runCatching { accessChecker.inspect(manifest.id, action) }
            .getOrElse {
                PluginAccessDecision.Unavailable("Access could not be verified safely.")
            }
        when (access) {
            PluginAccessDecision.Granted -> Unit
            is PluginAccessDecision.PermissionRequired -> return denied(
                now, request, action, access.explanation,
                PluginExecutionStatus.PERMISSION_REQUIRED,
                access
            )
            is PluginAccessDecision.CredentialRequired -> return denied(
                now, request, action, access.explanation,
                PluginExecutionStatus.CREDENTIAL_REQUIRED,
                access
            )
            is PluginAccessDecision.Unavailable -> return denied(
                now, request, action, access.explanation,
                PluginExecutionStatus.ACCESS_UNAVAILABLE,
                access
            )
        }

        if ((action.requiresConfirmation || handler.requiresConfirmation || action.capability.requiresConfirmation) && !request.userConfirmed) {
            return denied(
                now, request, action, "This action requires explicit confirmation.",
                PluginExecutionStatus.CONFIRMATION_REQUIRED
            )
        }
        val validationError = validateArguments(action, request.arguments)
        if (validationError != null) return denied(
            now, request, action, validationError, PluginExecutionStatus.INVALID_ARGUMENTS
        )
        if (handler.capability != action.capability) {
            return denied(
                now, request, action, "Plugin capability binding does not match.",
                PluginExecutionStatus.CAPABILITY_MISMATCH
            )
        }

        prune(globalCallTimes, now)
        val localTimes = pluginCallTimes.getOrPut(manifest.id) { ArrayDeque() }
        prune(localTimes, now)
        if (globalCallTimes.size >= globalCallsPerMinute ||
            localTimes.size >= manifest.maxCallsPerMinute
        ) {
            return denied(
                now, request, action, "Plugin rate limit reached.",
                PluginExecutionStatus.RATE_LIMITED
            )
        }

        if (!record(PluginAuditEntry(now, manifest.id, action.id, "requested"))) {
            return PluginExecutionResult(
                false, "Plugin audit is unavailable; execution was blocked.", action.capability,
                PluginExecutionStatus.AUDIT_UNAVAILABLE
            )
        }
        globalCallTimes.addLast(now)
        localTimes.addLast(now)

        val task: Future<PluginActionOutput> = try {
            EXECUTOR.submit<PluginActionOutput> { handler.execute(request.arguments) }
        } catch (_: RejectedExecutionException) {
            record(PluginAuditEntry(now, manifest.id, action.id, "capacity_exceeded"))
            return PluginExecutionResult(
                false, "Plugin execution capacity is full.", action.capability,
                PluginExecutionStatus.EXECUTION_CAPACITY_EXCEEDED
            )
        }
        return try {
            val output = task.get(manifest.maxExecutionMillis, TimeUnit.MILLISECONDS)
            if (output.message.length > maxOutputCharacters) {
                record(PluginAuditEntry(now, manifest.id, action.id, "oversized_output"))
                PluginExecutionResult(
                    false, "Plugin output exceeded the allowed size.", action.capability,
                    PluginExecutionStatus.OUTPUT_REJECTED
                )
            } else {
                record(PluginAuditEntry(now, manifest.id, action.id, if (output.success) "success" else "failed"))
                PluginExecutionResult(output.success, output.message, action.capability)
            }
        } catch (_: TimeoutException) {
            task.cancel(true)
            record(PluginAuditEntry(now, manifest.id, action.id, "timeout"))
            PluginExecutionResult(
                false, "Plugin exceeded its execution budget.", action.capability,
                PluginExecutionStatus.TIMED_OUT
            )
        } catch (error: ExecutionException) {
            val cause = error.cause
            val outcome = if (cause is SecurityException) "permission_denied" else "failed"
            record(PluginAuditEntry(now, manifest.id, action.id, outcome))
            PluginExecutionResult(
                false,
                if (cause is SecurityException) "Android denied this plugin operation." else "Plugin operation failed safely.",
                action.capability
            )
        } catch (_: InterruptedException) {
            task.cancel(true)
            Thread.currentThread().interrupt()
            record(PluginAuditEntry(now, manifest.id, action.id, "cancelled"))
            PluginExecutionResult(
                false, "Plugin operation was cancelled.", action.capability,
                PluginExecutionStatus.CANCELLED
            )
        }
    }

    private fun denied(
        now: Long,
        request: PluginExecutionRequest,
        action: PluginActionSpec?,
        message: String,
        status: PluginExecutionStatus = PluginExecutionStatus.FAILED,
        accessDecision: PluginAccessDecision? = null
    ): PluginExecutionResult {
        action?.let {
            val outcome = when (status) {
                PluginExecutionStatus.PERMISSION_REQUIRED -> "permission_required"
                PluginExecutionStatus.CREDENTIAL_REQUIRED -> "credential_required"
                PluginExecutionStatus.PLUGIN_NOT_CONNECTED -> "not_connected"
                PluginExecutionStatus.CONFIRMATION_REQUIRED -> "confirmation_required"
                else -> "denied"
            }
            record(PluginAuditEntry(now, request.pluginId, request.actionId, outcome))
        }
        return PluginExecutionResult(
            false,
            message,
            action?.capability ?: PluginCapability.DEVICE_INFORMATION,
            status,
            accessDecision
        )
    }

    private fun stableToolName(pluginId: String, actionId: String): String {
        val stableId = java.util.UUID.nameUUIDFromBytes(
            "$pluginId::$actionId".toByteArray(Charsets.UTF_8)
        ).toString().replace("-", "")
        return "plugin_$stableId"
    }

    private fun record(entry: PluginAuditEntry): Boolean = try {
        auditLog.append(entry)
        true
    } catch (_: Exception) {
        false
    }

    private fun prune(times: ArrayDeque<Long>, now: Long) {
        while (times.isNotEmpty() && now - times.first() >= 60_000L) times.removeFirst()
    }

    private fun validateManifest(manifest: PluginManifest) {
        require(PLUGIN_ID.matches(manifest.id)) { "Invalid plugin id." }
        require(VERSION.matches(manifest.version)) { "Invalid plugin version." }
        require(manifest.displayName.isNotBlank() && manifest.displayName.length <= 64)
        require(manifest.description.isNotBlank() && manifest.description.length <= 240)
        require(manifest.actions.isNotEmpty() && manifest.actions.size <= 16)
        require(manifest.maxCallsPerMinute in 1..12)
        require(manifest.maxExecutionMillis in 1L..5_000L)
        require(manifest.actions.map { it.id }.distinct().size == manifest.actions.size)
        require(manifest.capabilities == manifest.actions.map { it.capability }.toSet())
        manifest.actions.forEach { action ->
            require(ACTION_ID.matches(action.id)) { "Invalid action id." }
            val handler = handlersById[action.id]
            require(handler != null && handler.capability == action.capability) {
                "Action is not backed by a compiled allowlisted handler."
            }
            require(action.arguments == handler.argumentSchema) {
                "Manifest argument schema does not match the compiled handler schema."
            }
            require(action.arguments.size <= 8)
            action.arguments.forEach { (key, field) ->
                require(key == field.name && FIELD_ID.matches(key))
                require(field.maxLength in 1..maxInputCharacters)
                require(field.minimum == null || field.maximum == null || field.minimum <= field.maximum)
                require(field.allowedValues.size <= 32)
                when (field.type) {
                    PluginValueType.STRING -> require(field.minimum == null && field.maximum == null)
                    PluginValueType.INTEGER -> require(field.allowedValues.isEmpty())
                    PluginValueType.BOOLEAN -> require(
                        field.minimum == null && field.maximum == null && field.allowedValues.isEmpty()
                    )
                }
            }
        }
    }

    private fun validateArguments(
        action: PluginActionSpec,
        values: Map<String, PluginValue>
    ): String? {
        if (values.keys.any { it !in action.arguments }) return "Unknown plugin argument."
        val totalText = values.values.filterIsInstance<PluginValue.Text>().sumOf { it.value.length }
        if (totalText > maxInputCharacters) return "Plugin input exceeded the allowed size."
        for ((name, spec) in action.arguments) {
            val value = values[name]
            if (value == null) {
                if (spec.required) return "Required plugin argument is missing."
                continue
            }
            when (spec.type) {
                PluginValueType.STRING -> {
                    val text = (value as? PluginValue.Text)?.value ?: return "Plugin argument type mismatch."
                    if (text.length > spec.maxLength) return "Plugin argument is too long."
                    if (spec.allowedValues.isNotEmpty() && text !in spec.allowedValues) {
                        return "Plugin argument value is not allowed."
                    }
                }
                PluginValueType.INTEGER -> {
                    val number = (value as? PluginValue.Number)?.value ?: return "Plugin argument type mismatch."
                    if (spec.minimum != null && number < spec.minimum) return "Plugin argument is below its allowed range."
                    if (spec.maximum != null && number > spec.maximum) return "Plugin argument is above its allowed range."
                }
                PluginValueType.BOOLEAN -> if (value !is PluginValue.Flag) {
                    return "Plugin argument type mismatch."
                }
            }
        }
        return null
    }

    private companion object {
        private val EXECUTOR = ThreadPoolExecutor(
            2,
            2,
            0L,
            TimeUnit.MILLISECONDS,
            ArrayBlockingQueue(4),
            ThreadFactory { runnable ->
                Thread(runnable, "miss-minutes-plugin").apply { isDaemon = true }
            },
            ThreadPoolExecutor.AbortPolicy()
        )
        val PLUGIN_ID = Regex("^[a-z][a-z0-9_.-]{2,63}$")
        val ACTION_ID = Regex("^[a-z][a-z0-9_.-]{1,47}$")
        val FIELD_ID = Regex("^[a-z][a-zA-Z0-9_]{0,31}$")
        val VERSION = Regex("^\\d+\\.\\d+\\.\\d+$")
    }
}
