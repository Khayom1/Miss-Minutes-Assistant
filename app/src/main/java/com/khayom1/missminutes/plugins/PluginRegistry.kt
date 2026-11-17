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
    private val maxOutputCharacters: Int = 4_000
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

    @Synchronized
    fun uninstall(pluginId: String): Boolean = manifests.remove(pluginId) != null

    @Synchronized
    fun installedPlugins(): List<PluginManifest> = manifests.values.toList()

    @Synchronized
    fun execute(request: PluginExecutionRequest): PluginExecutionResult {
        val now = clockMillis()
        val manifest = manifests[request.pluginId]
            ?: return denied(now, request, null, "Plugin is not installed.")
        val action = manifest.actions.firstOrNull { it.id == request.actionId }
            ?: return denied(now, request, null, "Plugin action is not allowlisted.")
        val handler = handlersById[action.id]
            ?: return denied(now, request, action, "Plugin action handler is unavailable.")

        if (!consentStore.isGranted(manifest.id)) {
            return denied(now, request, action, "User consent for this plugin is required.")
        }
        if (action.requiresConfirmation && !request.userConfirmed) {
            return denied(now, request, action, "This plugin action requires confirmation.")
        }
        val validationError = validateArguments(action, request.arguments)
        if (validationError != null) return denied(now, request, action, validationError)
        if (handler.capability != action.capability) {
            return denied(now, request, action, "Plugin capability binding does not match.")
        }

        prune(globalCallTimes, now)
        val localTimes = pluginCallTimes.getOrPut(manifest.id) { ArrayDeque() }
        prune(localTimes, now)
        if (globalCallTimes.size >= globalCallsPerMinute ||
            localTimes.size >= manifest.maxCallsPerMinute
        ) {
            return denied(now, request, action, "Plugin rate limit reached.")
        }

        if (!record(PluginAuditEntry(now, manifest.id, action.id, "requested"))) {
            return PluginExecutionResult(false, "Plugin audit is unavailable; execution was blocked.", action.capability)
        }
        globalCallTimes.addLast(now)
        localTimes.addLast(now)

        val task: Future<PluginActionOutput> = try {
            EXECUTOR.submit<PluginActionOutput> { handler.execute(request.arguments) }
        } catch (_: RejectedExecutionException) {
            record(PluginAuditEntry(now, manifest.id, action.id, "capacity_exceeded"))
            return PluginExecutionResult(false, "Plugin execution capacity is full.", action.capability)
        }
        return try {
            val output = task.get(manifest.maxExecutionMillis, TimeUnit.MILLISECONDS)
            if (output.message.length > maxOutputCharacters) {
                record(PluginAuditEntry(now, manifest.id, action.id, "oversized_output"))
                PluginExecutionResult(false, "Plugin output exceeded the allowed size.", action.capability)
            } else {
                record(PluginAuditEntry(now, manifest.id, action.id, if (output.success) "success" else "failed"))
                PluginExecutionResult(output.success, output.message, action.capability)
            }
        } catch (_: TimeoutException) {
            task.cancel(true)
            record(PluginAuditEntry(now, manifest.id, action.id, "timeout"))
            PluginExecutionResult(false, "Plugin exceeded its execution budget.", action.capability)
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
            PluginExecutionResult(false, "Plugin operation was cancelled.", action.capability)
        }
    }

    private fun denied(
        now: Long,
        request: PluginExecutionRequest,
        action: PluginActionSpec?,
        message: String
    ): PluginExecutionResult {
        action?.let { record(PluginAuditEntry(now, request.pluginId, request.actionId, "denied")) }
        return PluginExecutionResult(false, message, action?.capability ?: PluginCapability.DEVICE_INFORMATION)
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
            require(handlersById[action.id]?.capability == action.capability) {
                "Action is not backed by a compiled allowlisted handler."
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
        val FIELD_ID = Regex("^[a-z][a-z0-9_]{0,31}$")
        val VERSION = Regex("^\\d+\\.\\d+\\.\\d+$")
    }
}
