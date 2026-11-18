package com.khayom1.missminutes.ai

enum class AiRuntimeProfile {
    FAST,
    STANDARD,
    MAX,
    EXTRA,
    AUTO
}

data class AiRuntimePlan(
    val requestedProfile: AiRuntimeProfile,
    val effectiveProfile: AiRuntimeProfile,
    val maxOutputTokens: Int,
    val maxToolRounds: Int,
    val maxToolCalls: Int,
    val maxHistoryMessages: Int,
    val maxContextCharacters: Int,
    val structuredToolsEnabled: Boolean
)

/**
 * Deterministic execution budgets. These profiles change real request/context
 * and tool-loop limits; they do not claim to change a provider's hidden model
 * reasoning mode.
 */
object AiRuntimePolicy {
    fun resolve(
        requestedProfile: AiRuntimeProfile,
        userMessage: String,
        connectedToolCount: Int,
        providerCapabilities: AiProviderCapabilities
    ): AiRuntimePlan {
        require(userMessage.isNotBlank())
        require(connectedToolCount >= 0)

        val usableToolCount = if (
            providerCapabilities.supportsStructuredTools && !providerCapabilities.isLocal
        ) connectedToolCount else 0
        val effective = if (requestedProfile == AiRuntimeProfile.AUTO) {
            chooseAutomatically(userMessage, usableToolCount, providerCapabilities)
        } else requestedProfile

        val base = when (effective) {
            AiRuntimeProfile.FAST -> Budget(192, 1, 2, 4, 8_000)
            AiRuntimeProfile.STANDARD -> Budget(384, 2, 4, 8, 10_000)
            AiRuntimeProfile.MAX -> Budget(640, 3, 6, 14, 14_000)
            AiRuntimeProfile.EXTRA -> Budget(768, 4, 8, 18, 16_000)
            AiRuntimeProfile.AUTO -> error("AUTO must be resolved before budgets are selected.")
        }
        val toolsEnabled = providerCapabilities.supportsStructuredTools && connectedToolCount > 0
        val localCap = providerCapabilities.isLocal

        return AiRuntimePlan(
            requestedProfile = requestedProfile,
            effectiveProfile = effective,
            maxOutputTokens = if (localCap) minOf(base.outputTokens, 384) else base.outputTokens,
            maxToolRounds = if (toolsEnabled && !localCap) base.toolRounds else 1,
            maxToolCalls = if (toolsEnabled && !localCap) base.toolCalls else 1,
            maxHistoryMessages = if (localCap) minOf(base.historyMessages, 8) else base.historyMessages,
            maxContextCharacters = if (localCap) minOf(base.contextCharacters, 10_000) else base.contextCharacters,
            structuredToolsEnabled = toolsEnabled && !localCap
        )
    }

    private fun chooseAutomatically(
        userMessage: String,
        connectedToolCount: Int,
        providerCapabilities: AiProviderCapabilities
    ): AiRuntimeProfile {
        val text = userMessage.lowercase()
        val deepSignals = listOf(
            "audit", "architecture", "refactor", "security review", "design a system",
            "multi-step", "implementation plan", "debug this code", "analyze this code",
            "аудит", "архитектур", "безопасност", "рефактор", "меъмор", "амният", "таҳлили пурра"
        )
        val mediumSignals = listOf(
            "explain", "compare", "debug", "plan", "why", "how does", "step by step",
            "муқоиса", "таҳлил", "чаро", "чӣ тавр", "нақша", "сравни", "почему", "как работает"
        )
        return when {
            userMessage.length >= 1_200 || connectedToolCount >= 4 ||
                deepSignals.any(text::contains) -> AiRuntimeProfile.EXTRA
            userMessage.length >= 320 || connectedToolCount >= 2 ||
                mediumSignals.any(text::contains) -> AiRuntimeProfile.MAX
            userMessage.length >= 100 || connectedToolCount == 1 -> AiRuntimeProfile.STANDARD
            providerCapabilities.isLocal -> AiRuntimeProfile.FAST
            else -> AiRuntimeProfile.FAST
        }
    }

    private data class Budget(
        val outputTokens: Int,
        val toolRounds: Int,
        val toolCalls: Int,
        val historyMessages: Int,
        val contextCharacters: Int
    )
}
