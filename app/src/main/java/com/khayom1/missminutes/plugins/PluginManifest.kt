package com.khayom1.missminutes.plugins

enum class PluginCapability {
    DEVICE_INFORMATION,
    BATTERY_STATUS
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

data class PluginExecutionResult(
    val success: Boolean,
    val message: String,
    val capability: PluginCapability
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
    fun execute(arguments: Map<String, PluginValue>): PluginActionOutput
}
