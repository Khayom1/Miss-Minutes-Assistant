package com.khayom1.missminutes.ai

import com.khayom1.missminutes.plugins.PluginFieldSpec
import com.khayom1.missminutes.plugins.PluginValueType
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull

/** Builds a bounded and schema-validated preview; sensitive field values are redacted. */
internal fun approvalArgumentPreview(
    arguments: String,
    schema: Map<String, PluginFieldSpec>
): List<String>? {
    if (arguments.length !in 2..2_000 || schema.size > 8) return null
    val values = runCatching { Json.parseToJsonElement(arguments).jsonObject }.getOrNull()
        ?: return null
    if (values.size > 8 || values.keys.any { it !in schema }) return null
    if (schema.values.any { it.required && it.name !in values }) return null

    val previews = mutableListOf<String>()
    for ((name, rawValue) in values.toSortedMap()) {
        val field = schema[name] ?: return null
        val primitive: JsonPrimitive = runCatching { rawValue.jsonPrimitive }.getOrNull() ?: return null
        val value = when (field.type) {
            PluginValueType.STRING -> {
                if (!primitive.isString || primitive.content.length > field.maxLength) return null
                if (field.allowedValues.isNotEmpty() && primitive.content !in field.allowedValues) return null
                primitive.content
            }
            PluginValueType.INTEGER -> {
                if (primitive.isString) return null
                val number = primitive.longOrNull ?: return null
                if (field.minimum != null && number < field.minimum) return null
                if (field.maximum != null && number > field.maximum) return null
                number.toString()
            }
            PluginValueType.BOOLEAN -> {
                if (primitive.isString) return null
                primitive.booleanOrNull?.toString() ?: return null
            }
        }
        val safeValue = if (SENSITIVE_FIELD.containsMatchIn(name)) {
            "[redacted]"
        } else {
            sanitizeApprovalDisplayText(value, MAX_PREVIEW_VALUE_CHARS)
        }
        previews += name + ": " + safeValue
    }
    return previews.takeIf { it.size <= 8 }
}

private val SENSITIVE_FIELD = Regex(
    "(?i)(password|passwd|secret|token|credential|api[_-]?key|authorization|otp|pin|one[_-]?time[_-]?code)"
)
private const val MAX_PREVIEW_VALUE_CHARS = 96

private val approvalBidiControls = setOf(
    0x061C, 0x200E, 0x200F, 0x202A, 0x202B, 0x202C,
    0x202D, 0x202E, 0x2066, 0x2067, 0x2068, 0x2069
).map { it.toChar() }.toSet()

/** Sanitizes untrusted plugin labels and preview values before displaying them. */
internal fun sanitizeApprovalDisplayText(value: String, maxLength: Int): String {
    require(maxLength in 1..512)
    return value
        .filterNot { character ->
            (Character.isISOControl(character) && !character.isWhitespace()) ||
                character in approvalBidiControls
        }
        .replace(Regex("\\s+"), " ")
        .trim()
        .take(maxLength)
}

data class AiToolApprovalPrompt(
    val toolName: String,
    val pluginName: String,
    val description: String,
    val argumentPreview: List<String>,
    val requestId: Long = 0L
) {
    init {
        require(toolName.isNotBlank() && toolName.length <= 64)
        require(pluginName.isNotBlank() && pluginName.length <= 128)
        require(description.length <= 512)
        require(argumentPreview.size <= 8)
        require(argumentPreview.all { it.length <= 140 })
        require(requestId >= 0L)
    }
}

/** Serializes consequential tool approvals with one-shot, expiring decisions. */
class AiToolApprovalGate(private val approvalTimeoutMillis: Long = DEFAULT_APPROVAL_TIMEOUT_MILLIS) {
    init {
        require(approvalTimeoutMillis in 1L..MAX_APPROVAL_TIMEOUT_MILLIS)
    }

    private val requestMutex = Mutex()
    private val mutablePending = MutableStateFlow<AiToolApprovalPrompt?>(null)
    val pending: StateFlow<AiToolApprovalPrompt?> = mutablePending.asStateFlow()

    @Volatile
    private var pendingDecision: CompletableDeferred<Boolean>? = null

    @Volatile
    private var pendingPrompt: AiToolApprovalPrompt? = null

    private var nextRequestId = 0L

    suspend fun awaitApproval(
        prompt: AiToolApprovalPrompt,
        isCurrent: () -> Boolean = { true }
    ): Boolean = requestMutex.withLock {
        if (!isCurrent()) return@withLock false
        val requestId = ++nextRequestId
        val requestPrompt = prompt.copy(requestId = requestId)
        val decision = CompletableDeferred<Boolean>()
        synchronized(this) {
            pendingDecision = decision
            pendingPrompt = requestPrompt
            mutablePending.value = requestPrompt
        }
        try {
            withTimeoutOrNull(approvalTimeoutMillis) { decision.await() } ?: false
        } finally {
            synchronized(this) {
                if (pendingDecision === decision) {
                    pendingDecision = null
                    pendingPrompt = null
                    mutablePending.value = null
                }
            }
        }
    }

    fun resolve(requestId: Long, approved: Boolean): Boolean = synchronized(this) {
        if (pendingPrompt?.requestId != requestId) return@synchronized false
        pendingDecision?.complete(approved) ?: false
    }

    fun rejectPending(): Boolean {
        val requestId = synchronized(this) { pendingPrompt?.requestId } ?: return false
        return resolve(requestId, approved = false)
    }

    private companion object {
        const val DEFAULT_APPROVAL_TIMEOUT_MILLIS = 60_000L
        const val MAX_APPROVAL_TIMEOUT_MILLIS = 120_000L
    }
}
