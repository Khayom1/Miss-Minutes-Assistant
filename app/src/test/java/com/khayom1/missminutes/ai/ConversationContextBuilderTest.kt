package com.khayom1.missminutes.ai

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ConversationContextBuilderTest {
    @Test
    fun savedMemoryIsExcludedByDefaultAndAddedOnlyAfterExplicitOptIn() {
        val history = listOf(AiMessage(AiRole.USER, "Earlier question"))
        val disabled = buildAiContextMessages(history, "Current question", listOf("private note"), false)
        assertFalse(disabled.any { it.role == AiRole.SYSTEM })
        assertFalse(disabled.any { it.content.contains("private note") })

        val enabled = buildAiContextMessages(history, "Current question", listOf("private note"), true)
        assertEquals(AiRole.SYSTEM, enabled.first().role)
        assertTrue(enabled.first().content.contains("private note"))
        assertEquals(AiRole.USER, enabled.last().role)
        assertEquals("Current question", enabled.last().content)
    }

    @Test
    fun memoryContextIsBoundedAndDoesNotDisplaceCurrentUserMessage() {
        val history = (1..30).map { AiMessage(AiRole.USER, "h".repeat(8_000)) }
        val memory = listOf("x".repeat(2_000), "y".repeat(2_000), "z".repeat(2_000))
        val result = buildAiContextMessages(history, "final question", memory, true)

        assertTrue(result.size <= 20)
        assertTrue(result.sumOf { it.content.length } <= 16_000)
        assertEquals(AiRole.SYSTEM, result.first().role)
        assertEquals("final question", result.last().content)
    }
}
