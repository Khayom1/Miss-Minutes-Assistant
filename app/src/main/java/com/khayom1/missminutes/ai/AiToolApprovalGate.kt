package com.khayom1.missminutes.ai

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

private val approvalBidiControls = setOf(
    0x061C, 0x200E, 0x200F, 0x202A, 0x202B, 0x202C,
    0x202D, 0x202E, 0x2066, 0x2067, 0x2068, 0x2069
).map { it.toChar() }.toSet()

/** Removes control and bidi-override characters from untrusted plugin labels before display. */
internal fun sanitizeApprovalDisplayText(value: String, maxLength: Int): String {
    require(maxLength in 1..512)
    return value
        .filterNot { character -> Character.isISOControl(character) || character in approvalBidiControls }
        .replace(Regex("\\s+"), " ")
        .trim()
        .take(maxLength)
}

/**
 * Serializes consequential tool approvals and exposes only a redacted prompt to
 * the UI. Raw argument values never enter Compose state.
 */
data class AiToolApprovalPrompt(
    val toolName: String,
    val pluginName: String,
    val description: String,
    val argumentNames: List<String>,
    val requestId: Long = 0L
) {
    init {
        require(toolName.matches(Regex("^[a-zA-Z0-9_-]{1,64}$")))
        require(pluginName.isNotBlank() && pluginName.length <= 128)
        require(description.length <= 512)
        require(argumentNames.size <= 16)
        require(argumentNames.all { it.matches(Regex("^[a-zA-Z0-9_-]{1,64}$")) })
        require(requestId >= 0L)
    }
}

/**
 * A tool call that requires confirmation suspends until the user explicitly
 * approves or rejects it. Cancellation always clears the pending request.
 */
class AiToolApprovalGate {
    private val requestMutex = Mutex()
    private val mutablePending = MutableStateFlow<AiToolApprovalPrompt?>(null)

    val pending: StateFlow<AiToolApprovalPrompt?> = mutablePending.asStateFlow()

    @Volatile
    private var pendingDecision: CompletableDeferred<Boolean>? = null

    @Volatile
    private var pendingPrompt: AiToolApprovalPrompt? = null

    private var nextRequestId = 0L

    suspend fun awaitApproval(prompt: AiToolApprovalPrompt): Boolean =
        requestMutex.withLock {
            val requestId = ++nextRequestId
            val requestPrompt = prompt.copy(requestId = requestId)
            val decision = CompletableDeferred<Boolean>()
            synchronized(this) {
                pendingDecision = decision
                pendingPrompt = requestPrompt
                mutablePending.value = requestPrompt
            }
            try {
                decision.await()
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

    /** A UI decision is accepted only for the exact approval currently displayed. */
    fun resolve(requestId: Long, approved: Boolean): Boolean = synchronized(this) {
        if (pendingPrompt?.requestId != requestId) return@synchronized false
        pendingDecision?.complete(approved) ?: false
    }

    /** Rejects a pending approval as soon as its owning conversation changes. */
    fun rejectPending(): Boolean {
        val requestId = synchronized(this) { pendingPrompt?.requestId } ?: return false
        return resolve(requestId, approved = false)
    }
}
