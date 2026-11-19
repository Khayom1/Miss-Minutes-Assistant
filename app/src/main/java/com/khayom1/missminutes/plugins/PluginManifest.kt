package com.khayom1.missminutes.plugins

enum class PluginCapability(val requiresConfirmation: Boolean = false) {
    DEVICE_INFORMATION,
    BATTERY_STATUS,
    CALENDAR_READ,
    CALENDAR_WRITE(true),
    ALARM_SCHEDULE(true),
    CONTACTS_READ,
    LOCATION_APPROXIMATE,
    LOCATION_PRECISE,
    NOTIFICATION_READ,
    ACCESSIBILITY_AUTOMATION,
    GITHUB_REPOSITORY_READ,
    GITHUB_REPOSITORY_WRITE(true)
}

enum class PluginValueType {
    STRING,
    INTEGER,
    BOOLEAN
}

sealed interface PluginValue {
    data class Text(val value: String) : PluginValue
    data class Number(val value: Long) : PluginValue
    data class Flag(val value: Boolean) : PluginValue
}

data class PluginFieldSpec(
    val name: String,
    val type: PluginValueType,
    val required: Boolean = true,
    val maxLength: Int = 256,
    val minimum: Long? = null,
    val maximum: Long? = null,
    val allowedValues: Set<String> = emptySet()
)

data class PluginActionSpec(
    val id: String,
    val capability: PluginCapability,
    val arguments: Map<String, PluginFieldSpec> = emptyMap(),
    val requiresConfirmation: Boolean = false
)

/** Declarative metadata only: no code, scripts, URLs, reflection or intents. */
data class PluginManifest(
    val id: String,
    val version: String,
    val displayName: String,
    val description: String,
    val capabilities: Set<PluginCapability>,
    val actions: List<PluginActionSpec>,
    val maxCallsPerMinute: Int = 6,
    val maxExecutionMillis: Long = 1_500L
)

data class PluginActionOutput(val success: Boolean, val message: String)

data class PluginExecutionRequest(
    val pluginId: String,
    val actionId: String,
    val arguments: Map<String, PluginValue> = emptyMap(),
    val userConfirmed: Boolean = false
)

enum class PluginExecutionStatus {
    SUCCEEDED,
    FAILED,
    PLUGIN_NOT_INSTALLED,
    ACTION_NOT_ALLOWED,
    HANDLER_UNAVAILABLE,
    PLUGIN_NOT_CONNECTED,
    PERMISSION_REQUIRED,
    USER_AUTHORIZATION_REQUIRED,
    CREDENTIAL_REQUIRED,
    ACCESS_UNAVAILABLE,
    CONFIRMATION_REQUIRED,
    INVALID_ARGUMENTS,
    CAPABILITY_MISMATCH,
    RATE_LIMITED,
    AUDIT_UNAVAILABLE,
    EXECUTION_CAPACITY_EXCEEDED,
    OUTPUT_REJECTED,
    TIMED_OUT,
    CANCELLED
}

data class PluginExecutionResult(
    val success: Boolean,
    val message: String,
    val capability: PluginCapability,
    val status: PluginExecutionStatus = if (success) {
        PluginExecutionStatus.SUCCEEDED
    } else {
        PluginExecutionStatus.FAILED
    },
    val accessDecision: PluginAccessDecision? = null
)

/**
 * Access is checked at execution time. It is deliberately separate from the
 * persistent plugin connection grant: connection is one-time, OS permissions
 * and provider credentials can expire or be revoked independently.
 */
sealed interface PluginAccessDecision {
    data object Granted : PluginAccessDecision

    data class PermissionRequired(
        val id: String,
        val label: String,
        val explanation: String,
        val androidPermission: String? = null,
        val settingsAction: String? = null
    ) : PluginAccessDecision

    data class UserAuthorizationRequired(
        val authorizationId: String,
        val explanation: String
    ) : PluginAccessDecision

    data class CredentialRequired(
        val provider: String,
        val explanation: String
    ) : PluginAccessDecision

    data class Unavailable(
        val explanation: String
    ) : PluginAccessDecision
}

fun interface PluginAccessChecker {
    fun inspect(pluginId: String, action: PluginActionSpec): PluginAccessDecision
}

data class PluginToolDescriptor(
    val toolName: String,
    val pluginId: String,
    val actionId: String,
    val pluginName: String,
    val description: String,
    val capability: PluginCapability,
    val arguments: Map<String, PluginFieldSpec>,
    val requiresConfirmation: Boolean,
    val connected: Boolean,
    val access: PluginAccessDecision,
    val sideEffect: Boolean = false
)

data class PluginAuditEntry(
    val timestampMillis: Long,
    val pluginId: String,
    val actionId: String,
    val outcome: String
)

fun interface PluginConsentStore {
    fun isGranted(pluginId: String): Boolean
}

/** Mutable local consent stores must support durable revocation. */
interface MutablePluginConsentStore : PluginConsentStore {
    fun setGranted(pluginId: String, granted: Boolean)
}

fun interface PluginAuditLog {
    fun append(entry: PluginAuditEntry)
}

interface PluginActionHandler {
    val actionId: String
    val capability: PluginCapability
    /** Compiled argument schema is authoritative; imported manifests cannot widen it. */
    val argumentSchema: Map<String, PluginFieldSpec> get() = emptyMap()
    /** Compiled safety floor: a manifest may require more confirmation, never less. */
    val requiresConfirmation: Boolean get() = false
    /** Compiled safety floor for duplicate-action suppression. */
    val sideEffect: Boolean get() = false
    fun execute(arguments: Map<String, PluginValue>): PluginActionOutput
}
