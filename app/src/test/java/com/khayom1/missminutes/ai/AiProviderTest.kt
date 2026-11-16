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

class AiProviderTest {
    private class FakeTokenSource(
        private val token: String?
    ) : AccessTokenSource {
        override suspend fun currentAccessToken(): String? = token
    }

    private class FakeTransport(
        private val response: AiHttpResponse = AiHttpResponse(
            statusCode = 200,
            body = """{"content":"Test response","model":"deepseek-chat"}"""
        )
    ) : AiHttpTransport {
        var calls = 0
        var capturedUrl: String? = null
        var capturedHeaders: Map<String, String> = emptyMap()
        var capturedBody: String? = null

        override suspend fun post(
            url: String,
            headers: Map<String, String>,
            body: String
        ): AiHttpResponse {
            calls++
            capturedUrl = url
            capturedHeaders = headers
            capturedBody = body
            return response
        }
    }

    private val validRequest = AiRequest(
        messages = listOf(
            AiMessage(AiRole.USER, "Hello Miss Minutes")
        )
    )

    private fun provider(
        token: String? = "user-session-jwt",
        transport: FakeTransport = FakeTransport()
    ) = SupabaseDeepSeekProvider(
        functionUrl = "https://project.example/functions/v1/miss-minutes-ai",
        publishableKey = "sb_publishable_public-test-key",
        accessTokenSource = FakeTokenSource(token),
        transport = transport
    )

    @Test
    fun remoteProviderUsesUserJwtAndPublishableKeyOnly() = runBlocking {
        val transport = FakeTransport()
        val completion = provider(transport = transport).complete(validRequest)

        assertEquals("Test response", completion.content)
        assertEquals("deepseek-chat", completion.model)
        assertEquals("https://project.example/functions/v1/miss-minutes-ai", transport.capturedUrl)
        assertEquals("Bearer user-session-jwt", transport.capturedHeaders["Authorization"])
        assertEquals("sb_publishable_public-test-key", transport.capturedHeaders["apikey"])
        assertFalse(transport.capturedHeaders.values.any { it.contains("deepseek-secret") })

        val payload = Json.parseToJsonElement(transport.capturedBody!!).jsonObject
        val messages = payload["messages"]!!.jsonArray
        assertEquals(1, messages.size)
        assertEquals("user", messages[0].jsonObject["role"]!!.jsonPrimitive.content)
        assertEquals("Hello Miss Minutes", messages[0].jsonObject["content"]!!.jsonPrimitive.content)
    }

    @Test
    fun remoteProviderRequiresSignedInSessionBeforeNetworkRequest() = runBlocking {
        val transport = FakeTransport()
        val error = runCatching {
            provider(token = null, transport = transport).complete(validRequest)
        }.exceptionOrNull()

        assertTrue(error is AiProviderException.AuthenticationRequired)
        assertEquals(0, transport.calls)
    }

    @Test
    fun remoteProviderMapsRateLimitToTypedError() = runBlocking {
        val transport = FakeTransport(AiHttpResponse(429, """{"error":"rate limited"}"""))
        val error = runCatching {
            provider(transport = transport).complete(validRequest)
        }.exceptionOrNull()

        assertTrue(error is AiProviderException.RateLimited)
    }

    @Test
    fun remoteProviderDoesNotExposeBackendErrorBody() = runBlocking {
        val transport = FakeTransport(AiHttpResponse(500, """{"error":"secret internal details"}"""))
        val error = runCatching {
            provider(transport = transport).complete(validRequest)
        }.exceptionOrNull()

        assertTrue(error is AiProviderException.Unavailable)
        assertFalse(error?.message.orEmpty().contains("secret internal details"))
    }

    @Test
    fun remoteProviderRejectsMalformedSuccessfulResponse() = runBlocking {
        val transport = FakeTransport(AiHttpResponse(200, """{"unexpected":"shape"}"""))
        val error = runCatching {
            provider(transport = transport).complete(validRequest)
        }.exceptionOrNull()

        assertTrue(error is AiProviderException.InvalidResponse)
    }

    @Test
    fun requestRejectsNonUserFinalMessageAndExcessiveOutputLimit() {
        val wrongFinalRole = runCatching {
            AiRequest(listOf(AiMessage(AiRole.ASSISTANT, "This should not be the final input")))
        }.exceptionOrNull()
        val excessiveTokens = runCatching {
            AiRequest(listOf(AiMessage(AiRole.USER, "hello")), maxOutputTokens = 4_096)
        }.exceptionOrNull()

        assertTrue(wrongFinalRole is IllegalArgumentException)
        assertTrue(excessiveTokens is IllegalArgumentException)
    }

    @Test
    fun localProviderUsesInjectedRuntimeWithoutNetworkCredentials() = runBlocking {
        val runtime = object : LocalInferenceRuntime {
            override fun isAvailable(): Boolean = true

            override suspend fun generate(
                messages: List<AiMessage>,
                maxOutputTokens: Int
            ): String {
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
            override suspend fun generate(
                messages: List<AiMessage>,
                maxOutputTokens: Int
            ): String = error("Must not call an unavailable runtime")
        }

        val error = runCatching {
            OptionalLocalAiProvider(runtime).complete(validRequest)
        }.exceptionOrNull()

        assertTrue(error is AiProviderException.Unavailable)
    }
}
