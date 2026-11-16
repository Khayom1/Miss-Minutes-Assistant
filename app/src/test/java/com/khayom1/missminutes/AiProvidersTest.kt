package com.khayom1.missminutes

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AiProvidersTest {\n    @Test fun infersNativeGeminiAndAnthropicProtocolsFromOfficialHosts() {\n        assertEquals(ProviderProtocol.GEMINI, inferProviderProtocol("https://generativelanguage.googleapis.com/v1beta"))\n        assertEquals(ProviderProtocol.ANTHROPIC, inferProviderProtocol("https://api.anthropic.com/v1"))\n        assertEquals(ProviderProtocol.OPENAI_COMPATIBLE, inferProviderProtocol("https://api.deepseek.com"))\n    }\n\n    @Test fun buildsGeminiNativeRequestWithSystemInstructionAndOrderedTurns() {\n        val request = buildGeminiGenerateContentRequest(listOf(AiMessage("system", "Be helpful"), AiMessage("user", "Hello"), AiMessage("assistant", "Hi"), AiMessage("user", "Again")))\n        assertEquals("Be helpful", request.getJSONObject("systemInstruction").getJSONArray("parts").getJSONObject(0).getString("text"))\n        val contents = request.getJSONArray("contents")\n        assertEquals(3, contents.length())\n        assertEquals("user", contents.getJSONObject(0).getString("role"))\n        assertEquals("model", contents.getJSONObject(1).getString("role"))\n        assertEquals("Again", contents.getJSONObject(2).getJSONArray("parts").getJSONObject(0).getString("text"))\n    }\n\n    @Test fun buildsAnthropicNativeMessagesRequestAndMovesSystemOutOfMessages() {\n        val request = buildAnthropicMessagesRequest("claude-sonnet-4-6", listOf(AiMessage("system", "Be helpful"), AiMessage("user", "Hello"), AiMessage("assistant", "Hi")))\n        assertEquals("claude-sonnet-4-6", request.getString("model"))\n        assertEquals("Be helpful", request.getString("system"))\n        val messages = request.getJSONArray("messages")\n        assertEquals(2, messages.length())\n        assertEquals("user", messages.getJSONObject(0).getString("role"))\n        assertEquals("assistant", messages.getJSONObject(1).getString("role"))\n    }\n\n    @Test fun parsesGeminiAndAnthropicTextBlocks() {\n        assertEquals("Gemini answer", parseGeminiGenerateContent("""{"candidates":[{"finishReason":"STOP","content":{"parts":[{"text":"Gemini answer"}]}}]}"""))\n        assertEquals("Claude answer", parseAnthropicMessages("""{"stop_reason":"end_turn","content":[{"type":"text","text":"Claude answer"}]}"""))\n    }\n\n    @Test fun buildsOfficialNativeProviderEndpoints() {\n        assertEquals("https://generativelanguage.googleapis.com/v1beta/models/gemini-2.5-flash:generateContent", resolveGeminiEndpoint("https://generativelanguage.googleapis.com/v1beta", "gemini-2.5-flash"))\n        assertEquals("https://api.anthropic.com/v1/messages", resolveAnthropicMessagesEndpoint("https://api.anthropic.com"))\n    }\n\n    @Test(expected = IllegalArgumentException::class) fun rejectsGeminiHostSpoofing() {\n        resolveGeminiEndpoint("https://evil.example.com/v1beta", "gemini-2.5-flash")\n    }\n\n    @Test(expected = IllegalArgumentException::class) fun rejectsAnthropicHostSpoofing() {\n        resolveAnthropicMessagesEndpoint("https://evil.example.com/v1")\n    }
    @Test
    fun appendsChatCompletionsToBaseUrl() {
        assertEquals(
            "https://api.example.com/v1/chat/completions",
            resolveOpenAiChatEndpoint("https://api.example.com/v1")
        )
    }

    @Test
    fun doesNotDuplicateExistingChatCompletionsPath() {
        assertEquals(
            "https://api.example.com/v1/chat/completions",
            resolveOpenAiChatEndpoint("https://api.example.com/v1/chat/completions/")
        )
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsCleartextHttpEndpoint() {
        resolveOpenAiChatEndpoint("http://api.example.com/v1")
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsCredentialsEmbeddedInEndpoint() {
        resolveOpenAiChatEndpoint("https://user:password@api.example.com/v1")
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsQueryParametersInEndpoint() {
        resolveOpenAiChatEndpoint("https://api.example.com/v1?key=secret")
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsEndpointWithoutHost() {
        resolveOpenAiChatEndpoint("https:///v1")
    }

    @Test
    fun requestUsesConfiguredModelAndPreservesRoleOrder() {
        val request = buildOpenAiChatCompletionRequest(
            "custom-model",
            listOf(
                AiMessage("system", "system context"),
                AiMessage("user", "first question"),
                AiMessage("assistant", "first answer"),
                AiMessage("user", "follow-up")
            )
        )

        assertEquals("custom-model", request.getString("model"))
        assertFalse(request.getBoolean("stream"))
        val messages = request.getJSONArray("messages")
        assertEquals(4, messages.length())
        assertEquals("system", messages.getJSONObject(0).getString("role"))
        assertEquals("system context", messages.getJSONObject(0).getString("content"))
        assertEquals("user", messages.getJSONObject(1).getString("role"))
        assertEquals("assistant", messages.getJSONObject(2).getString("role"))
        assertEquals("first answer", messages.getJSONObject(2).getString("content"))
        assertEquals("follow-up", messages.getJSONObject(3).getString("content"))
    }

    @Test
    fun parserExtractsAssistantMessage() {
        val payload = """{"choices":[{"message":{"role":"assistant","content":"Hello from provider"}}]}"""
        assertEquals("Hello from provider", parseOpenAiChatCompletion(payload))
    }

    @Test(expected = IllegalStateException::class)
    fun parserRejectsMalformedJson() {
        parseOpenAiChatCompletion("not-json")
    }

    @Test(expected = IllegalStateException::class)
    fun parserRejectsEmptyChoices() {
        parseOpenAiChatCompletion("""{"choices":[]}""")
    }

    @Test(expected = IllegalStateException::class)
    fun parserRejectsEmptyContent() {
        parseOpenAiChatCompletion("""{"choices":[{"message":{"content":"  "}}]}""")
    }

    @Test
    fun registryExposesOnlyExplicitlySupportedProtocolCapabilities() {
        val provider = ProviderRegistry.resolve(ProviderProtocol.OPENAI_COMPATIBLE)
        assertEquals(ProviderProtocol.OPENAI_COMPATIBLE, provider.protocol)
        assertTrue(provider.capabilities.supportsSystemMessages)
        assertTrue(provider.capabilities.supportsConversationHistory)
        assertTrue(provider.capabilities.supportsModelSelection)
        assertFalse(provider.capabilities.supportsStreaming)
    }

    @Test(expected = IllegalArgumentException::class)
    fun profileRequiresValidIdentifier() {
        ProviderConfig("invalid id", "Local Gateway", "https://api.example.com/v1", "model").validate("key")
    }

    @Test(expected = IllegalArgumentException::class)
    fun profileRequiresModelId() {
        ProviderConfig("primary", "Local Gateway", "https://api.example.com/v1", " ").validate("key")
    }
}
