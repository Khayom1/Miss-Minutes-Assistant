package com.khayom1.missminutes.ai

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
    private class FakeTransport(private val response: AiHttpResponse = AiHttpResponse(
        200,
        """{"model":"deepseek-chat","choices":[{"message":{"content":"Hello from DeepSeek"}}]}"""
    )) : AiHttpTransport {
        var calls = 0
        var url: String? = null
        var headers: Map<String, String> = emptyMap()
        var body: String? = null

        override suspend fun post(url: String, headers: Map<String, String>, body: String): AiHttpResponse {
            calls++
            this.url = url
            this.headers = headers
            this.body = body
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
}
