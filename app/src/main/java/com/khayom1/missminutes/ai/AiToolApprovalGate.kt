package com.khayom1.missminutes.ai

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Serializes consequential tool approvals and exposes only a redacted prompt to
 * the UI. Raw argument values never enter Compose state.
 */
data class AiToolApprovalPrompt(
    val toolName: String,
    val pluginName: String,
    val description: String,
    val argumentNames: List<String>
) {
    init {
        require(toolName.matches(Regex("^[a-zA-Z0-9_-]{1,64}$")))
        require(pluginName.isNotBlank() && pluginName.length <= 128)
        require(description.length <= 512)
        require(argumentNames.size <= 16)
        require(argumentNames.all { it.matches(Regex("^[a-zA-Z0-9_-]{1,64}$")) })
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

    suspend fun awaitApproval(prompt: AiToolApprovalPrompt): Boolean =
        requestMutex.withLock {
            val decision = CompletableDeferred<Boolean>()
            pendingDecision = decision
            mutablePending.value = prompt
            try {
                decision.await()
            } finally {
                if (pendingDecision === decision) pendingDecision = null
                mutablePending.value = null
            }
        }

    /** Returns false when no approval is awaiting a user decision. */
    fun resolve(approved: Boolean): Boolean =
        pendingDecision?.complete(approved) ?: false
}
