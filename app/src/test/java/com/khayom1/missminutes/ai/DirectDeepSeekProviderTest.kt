package com.khayom1.missminutes.ai

import java.net.SocketTimeoutException
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DirectDeepSeekProviderTest {
    private class FakeTransport(
        private val response: AiHttpResponse = AiHttpResponse(
            200,
            """{"model":"deepseek-chat","choices":[{"message":{"content":"Hello from DeepSeek"}}]}"""
        ),
        private val failure: Throwable? = null
    ) : AiHttpTransport {
        var calls = 0
        var url: String? = null
        var headers: Map<String, String> = emptyMap()
        var body: String? = null

        override suspend fun post(url: String, headers: Map<String, String>, body: String): AiHttpResponse {
            calls++
            this.url = url
            this.headers = headers
            this.body = body
            failure?.let { throw it }
            return response
        }
    }

    private val request = AiRequest(listOf(AiMessage(AiRole.USER, "Hello Miss Minutes")))

    @Test
    fun sendsUserOwnedKeyDirectlyToDeepSeek() = runBlocking {
        val transport = FakeTransport()
        val provider = DirectDeepSeekProvider(DeepSeekApiKeySource { "sk-user-owned-test-key-123456" }, transport)
        val result = provider.complete(request)

        assertEquals("Hello from DeepSeek", result.content)
        assertEquals("deepseek-chat", result.model)
        assertEquals("https://api.deepseek.com/chat/completions", transport.url)
        assertEquals("Bearer sk-user-owned-test-key-123456", transport.headers["Authorization"])
        assertEquals("application/json", transport.headers["Content-Type"])
        assertFalse(transport.headers.values.any { it.contains("supabase") })
        val payload = Json.parseToJsonElement(transport.body!!).jsonObject
        assertEquals("deepseek-chat", payload["model"]!!.jsonPrimitive.content)
        assertEquals("false", payload["stream"]!!.jsonPrimitive.content)
        assertEquals("user", payload["messages"]!!.jsonArray.single().jsonObject["role"]!!.jsonPrimitive.content)
    }

    @Test
    fun missingKeyFailsBeforeNetworkCall() = runBlocking {
        val transport = FakeTransport()
        val error = runCatching {
            DirectDeepSeekProvider(DeepSeekApiKeySource { null }, transport).complete(request)
        }.exceptionOrNull()
        assertTrue(error is AiProviderException.MissingApiKey)
        assertEquals(0, transport.calls)
    }

    @Test
    fun mapsInvalidDeepSeekKeyToTypedInvalidKeyError() = runBlocking {
        val transport = FakeTransport(AiHttpResponse(401, """{"error":"invalid key"}"""))
        val error = runCatching {
            DirectDeepSeekProvider(DeepSeekApiKeySource { "sk-invalid-test-key-123456" }, transport).complete(request)
        }.exceptionOrNull()
        assertTrue(error is AiProviderException.InvalidApiKey)
        assertFalse(error?.message.orEmpty().contains("invalid key"))
    }

    @Test
    fun mapsRateLimitAndProviderFailureToTypedErrors() = runBlocking {
        val limited = runCatching {
            DirectDeepSeekProvider(
                DeepSeekApiKeySource { "sk-user-owned-test-key-123456" },
                FakeTransport(AiHttpResponse(429, """{"error":"limited"}"""))
            ).complete(request)
        }.exceptionOrNull()
        val unavailable = runCatching {
            DirectDeepSeekProvider(
                DeepSeekApiKeySource { "sk-user-owned-test-key-123456" },
                FakeTransport(AiHttpResponse(503, "internal detail"))
            ).complete(request)
        }.exceptionOrNull()
        assertTrue(limited is AiProviderException.RateLimited)
        assertTrue(unavailable is AiProviderException.Unavailable)
        assertFalse(unavailable?.message.orEmpty().contains("internal detail"))
    }

    @Test
    fun rejectsMalformedSuccessPayload() = runBlocking {
        val error = runCatching {
            DirectDeepSeekProvider(
                DeepSeekApiKeySource { "sk-user-owned-test-key-123456" },
                FakeTransport(AiHttpResponse(200, """{"choices":[]}"""))
            ).complete(request)
        }.exceptionOrNull()
        assertTrue(error is AiProviderException.InvalidResponse)
    }

    @Test
    fun rejectsNonJsonSuccessBody() = runBlocking {
        val error = runCatching {
            DirectDeepSeekProvider(
                DeepSeekApiKeySource { "sk-user-owned-test-key-123456" },
                FakeTransport(AiHttpResponse(200, "not-json"))
            ).complete(request)
        }.exceptionOrNull()
        assertTrue(error is AiProviderException.InvalidResponse)
    }

    @Test
    fun mapsTransportTimeoutToTypedTimeoutWithoutLeakingCredentials() = runBlocking {
        val apiKey = "sk-secret-test-credential-123456"
        val transport = FakeTransport(failure = SocketTimeoutException("timeout"))
        val error = runCatching {
            DirectDeepSeekProvider(DeepSeekApiKeySource { apiKey }, transport).complete(request)
        }.exceptionOrNull()
        assertTrue(error is AiProviderException.Timeout)
        assertFalse(error?.message.orEmpty().contains(apiKey))
        assertEquals(1, transport.calls)
    }

    @Test
    fun mapsUnexpectedClientErrorsToSanitizedProviderRejection() = runBlocking {
        val apiKey = "sk-secret-test-credential-123456"
        val error = runCatching {
            DirectDeepSeekProvider(
                DeepSeekApiKeySource { apiKey },
                FakeTransport(AiHttpResponse(400, """{"error":"$apiKey private detail"}"""))
            ).complete(request)
        }.exceptionOrNull()
        assertTrue(error is AiProviderException.ProviderRejected)
        assertFalse(error?.message.orEmpty().contains(apiKey))
    }

    @Test
    fun mapsRequestTimeoutStatusAndDoesNotRetryProviderCalls() = runBlocking {
        val transport = FakeTransport(AiHttpResponse(408, """{"error":"timeout"}"""))
        val error = runCatching {
            DirectDeepSeekProvider(
                DeepSeekApiKeySource { "sk-user-owned-test-key-123456" },
                transport
            ).complete(request)
        }.exceptionOrNull()
        assertTrue(error is AiProviderException.Timeout)
        assertEquals(1, transport.calls)
    }

    @Test
    fun sendsConnectedToolSchemaAndParsesFunctionCalls() = runBlocking {
        val response = AiHttpResponse(
            200,
            """{"model":"deepseek-chat","choices":[{"message":{"content":null,"tool_calls":[{"id":"call_status_1","type":"function","function":{"name":"plugin_status","arguments":"{}"}}]}}]}"""
        )
        val transport = FakeTransport(response)
        val schema = Json.parseToJsonElement(
            """{"type":"object","properties":{},"required":[],"additionalProperties":false}"""
        ).jsonObject
        val requestWithTool = AiRequest(
            messages = listOf(AiMessage(AiRole.USER, "Read device status")),
            tools = listOf(AiToolDefinition("plugin_status", "Read device status", schema))
        )
        val result = DirectDeepSeekProvider(
            DeepSeekApiKeySource { "sk-user-owned-test-key-123456" },
            transport
        ).complete(requestWithTool)

        assertEquals("plugin_status", result.toolCalls.single().name)
        assertEquals("{}", result.toolCalls.single().arguments)
        val payload = Json.parseToJsonElement(transport.body!!).jsonObject
        assertEquals("auto", payload["tool_choice"]!!.jsonPrimitive.content)
        assertEquals("plugin_status", payload["tools"]!!.jsonArray.single().jsonObject
            ["function"]!!.jsonObject["name"]!!.jsonPrimitive.content)
    }

    @Test
    fun serializesAssistantToolCallsAndToolResultsForFollowUpRequests() = runBlocking {
        val transport = FakeTransport()
        val schema = Json.parseToJsonElement(
            """{"type":"object","properties":{},"required":[],"additionalProperties":false}"""
        ).jsonObject
        val tool = AiToolDefinition("plugin_status", "Read device status", schema)
        val requestWithToolResult = AiRequest(
            messages = listOf(
                AiMessage(AiRole.USER, "Read device status"),
                AiMessage(
                    AiRole.ASSISTANT,
                    "",
                    toolCalls = listOf(AiToolCall("call_status_1", "plugin_status", "{}"))
                ),
                AiMessage(
                    AiRole.TOOL,
                    "SUCCESS: 78 percent battery",
                    toolCallId = "call_status_1",
                    name = "plugin_status"
                )
            ),
            tools = listOf(tool)
        )
        DirectDeepSeekProvider(
            DeepSeekApiKeySource { "sk-user-owned-test-key-123456" },
            transport
        ).complete(requestWithToolResult)

        val messages = Json.parseToJsonElement(transport.body!!).jsonObject["messages"]!!.jsonArray
        assertEquals("call_status_1", messages[1].jsonObject["tool_calls"]!!.jsonArray.single()
            .jsonObject["id"]!!.jsonPrimitive.content)
        assertEquals("call_status_1", messages[2].jsonObject["tool_call_id"]!!.jsonPrimitive.content)
        assertEquals("plugin_status", messages[2].jsonObject["name"]!!.jsonPrimitive.content)
    }


    @Test
    fun rejectsNonFunctionToolCallTypes() = runBlocking {
        val response = AiHttpResponse(
            200,
            """{"model":"deepseek-chat","choices":[{"message":{"content":null,"tool_calls":[{"id":"call_1","type":"shell","function":{"name":"plugin_status","arguments":"{}"}}]}}]}"""
        )
        val schema = Json.parseToJsonElement(
            """{"type":"object","properties":{},"required":[],"additionalProperties":false}"""
        ).jsonObject
        val requestWithTool = AiRequest(
            messages = listOf(AiMessage(AiRole.USER, "Read device status")),
            tools = listOf(AiToolDefinition("plugin_status", "Read device status", schema))
        )
        val error = runCatching {
            DirectDeepSeekProvider(
                DeepSeekApiKeySource { "sk-user-owned-test-key-123456" },
                FakeTransport(response)
            ).complete(requestWithTool)
        }.exceptionOrNull()
        assertTrue(error is AiProviderException.InvalidResponse)
    }
}
