package com.khayom1.missminutes.ai

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AiRuntimeProfilesTest {
    private val onlineTools = AiProviderCapabilities(
        supportsStructuredTools = true,
        isLocal = false
    )

    @Test
    fun explicitProfilesHaveDistinctOutputContextAndToolBudgets() {
        val plans = listOf(
            AiRuntimeProfile.FAST,
            AiRuntimeProfile.STANDARD,
            AiRuntimeProfile.MAX,
            AiRuntimeProfile.EXTRA
        ).map { profile ->
            AiRuntimePolicy.resolve(profile, "A bounded request", 5, onlineTools)
        }

        assertEquals(listOf(192, 384, 640, 768), plans.map { it.maxOutputTokens })
        assertEquals(listOf(4, 8, 14, 18), plans.map { it.maxHistoryMessages })
        assertEquals(listOf(1, 2, 3, 4), plans.map { it.maxToolRounds })
        assertEquals(listOf(2, 4, 6, 8), plans.map { it.maxToolCalls })
        assertEquals(listOf(8_000, 10_000, 14_000, 16_000), plans.map { it.maxContextCharacters })
    }

    @Test
    fun autoSelectsBudgetFromRequestComplexityAndConnectedTools() {
        assertEquals(
            AiRuntimeProfile.FAST,
            AiRuntimePolicy.resolve(AiRuntimeProfile.AUTO, "Hi", 0, onlineTools).effectiveProfile
        )
        assertEquals(
            AiRuntimeProfile.STANDARD,
            AiRuntimePolicy.resolve(AiRuntimeProfile.AUTO, "A".repeat(120), 0, onlineTools).effectiveProfile
        )
        assertEquals(
            AiRuntimeProfile.MAX,
            AiRuntimePolicy.resolve(AiRuntimeProfile.AUTO, "Please compare these two approaches", 1, onlineTools).effectiveProfile
        )
        assertEquals(
            AiRuntimeProfile.EXTRA,
            AiRuntimePolicy.resolve(AiRuntimeProfile.AUTO, "Perform a security review of this architecture", 2, onlineTools).effectiveProfile
        )
    }

    @Test
    fun autoIgnoresConnectedToolsWhenProviderCannotExecuteStructuredTools() {
        val unsupported = AiProviderCapabilities(supportsStructuredTools = false, isLocal = false)
        val plan = AiRuntimePolicy.resolve(AiRuntimeProfile.AUTO, "Hi", 8, unsupported)

        assertEquals(AiRuntimeProfile.FAST, plan.effectiveProfile)
        assertFalse(plan.structuredToolsEnabled)
        assertEquals(1, plan.maxToolRounds)
        assertEquals(1, plan.maxToolCalls)
    }

    @Test
    fun providerWithoutStructuredToolsNeverReceivesToolBudget() {
        val local = AiProviderCapabilities(supportsStructuredTools = false, isLocal = true)
        val plan = AiRuntimePolicy.resolve(AiRuntimeProfile.EXTRA, "Long request", 8, local)

        assertFalse(plan.structuredToolsEnabled)
        assertEquals(1, plan.maxToolRounds)
        assertEquals(1, plan.maxToolCalls)
        assertEquals(384, plan.maxOutputTokens)
        assertEquals(8, plan.maxHistoryMessages)
        assertEquals(10_000, plan.maxContextCharacters)
    }

    @Test
    fun fastProfileDoesNotSilentlyDisableSupportedConnectedTools() {
        val plan = AiRuntimePolicy.resolve(AiRuntimeProfile.FAST, "Read battery status", 1, onlineTools)

        assertTrue(plan.structuredToolsEnabled)
        assertEquals(1, plan.maxToolRounds)
        assertEquals(2, plan.maxToolCalls)
    }
}
