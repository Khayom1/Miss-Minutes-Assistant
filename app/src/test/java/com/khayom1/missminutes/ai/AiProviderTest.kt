package com.khayom1.missminutes.ai

import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
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
    fun localProviderUsesOnlyTheInjectedRuntime() = runBlocking {
        val runtime = object : LocalInferenceRuntime {
            override fun isAvailable(): Boolean = true
            override suspend fun generate(messages: List<AiMessage>, maxOutputTokens: Int): String {
                assertEquals("Hello Miss Minutes", messages.single().content)
                assertEquals(768, maxOutputTokens)
                return "Local model response"
            }
        }
        val completion = OptionalLocalAiProvider(runtime).complete(validRequest)
        assertEquals("Local model response", completion.content)
        assertEquals("local", completion.model)
    }

    @Test
    fun localProviderFailsClosedWhenRuntimeIsUnavailable() = runBlocking {
        val runtime = object : LocalInferenceRuntime {
            override fun isAvailable(): Boolean = false
            override suspend fun generate(messages: List<AiMessage>, maxOutputTokens: Int): String =
                error("Unavailable runtimes must not be called")
        }
        val error = runCatching { OptionalLocalAiProvider(runtime).complete(validRequest) }.exceptionOrNull()
        assertTrue(error is AiProviderException.Unavailable)
    }

    @Test
    fun localProviderRejectsBlankOutput() = runBlocking {
        val runtime = object : LocalInferenceRuntime {
            override fun isAvailable(): Boolean = true
            override suspend fun generate(messages: List<AiMessage>, maxOutputTokens: Int): String = "   "
        }
        val error = runCatching { OptionalLocalAiProvider(runtime).complete(validRequest) }.exceptionOrNull()
        assertTrue(error is AiProviderException.InvalidResponse)
    }
 
    @Test
    fun localProviderRejectsStructuredToolRequestsWithoutCallingRuntime() = runBlocking {
        val schema = Json.parseToJsonElement(
            """{"type":"object","properties":{},"required":[],"additionalProperties":false}"""
        ).jsonObject
        var generated = false
        val runtime = object : LocalInferenceRuntime {
            override fun isAvailable(): Boolean = true
            override suspend fun generate(messages: List<AiMessage>, maxOutputTokens: Int): String {
                generated = true
                return "not a structured tool response"
            }
        }
        val request = validRequest.copy(
            tools = listOf(AiToolDefinition("plugin_status", "Read device status", schema))
        )
        val error = runCatching { OptionalLocalAiProvider(runtime).complete(request) }.exceptionOrNull()
        assertTrue(error is AiProviderException.Unavailable)
        assertFalse(generated)
    }
}
