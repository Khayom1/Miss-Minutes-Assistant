package com.khayom1.missminutes.ai

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AiProviderTest {
    private val validRequest = AiRequest(
        messages = listOf(AiMessage(AiRole.USER, "Hello Miss Minutes"))
    )

    @Test
    fun requestRejectsNonUserFinalMessageAndExcessiveOutputLimit() {
        val wrongFinalRole = runCatching {
            AiRequest(listOf(AiMessage(AiRole.ASSISTANT, "This cannot be the final input")))
        }.exceptionOrNull()
        val excessiveTokens = runCatching {
            AiRequest(listOf(AiMessage(AiRole.USER, "hello")), maxOutputTokens = 4_096)
        }.exceptionOrNull()
        assertTrue(wrongFinalRole is IllegalArgumentException)
        assertTrue(excessiveTokens is IllegalArgumentException)
    }

    @Test
    fun localInferenceIsExplicitlyUnavailableWithoutAnInstalledRuntime() {
        assertFalse(LocalInferenceStatus.isAvailable)
        assertTrue(LocalInferenceStatus.reason.isNotBlank())
    }

}
