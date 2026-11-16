package com.khayom1.missminutes.ai

import kotlinx.coroutines.runBlocking
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ChatProviderTest {

    @Test
    fun backendEndpointRequiresSameSupabaseHostAndExactHttpsPath() {
        val base = "https://project.supabase.co"
        assertEquals(
            "https://project.supabase.co/functions/v1/deepseek-proxy".toHttpUrl(),
            BackendEndpointPolicy.parse(
                "https://project.supabase.co/functions/v1/deepseek-proxy",
                base
            )
        )
        assertEquals(
            null,
            BackendEndpointPolicy.parse(
                "http://project.supabase.co/functions/v1/deepseek-proxy",
                base
            )
        )
        assertEquals(
            null,
            BackendEndpointPolicy.parse(
                "https://attacker.test/functions/v1/deepseek-proxy",
                base
            )
        )
        assertEquals(
            null,
            BackendEndpointPolicy.parse(
                "https://project.supabase.co/functions/v1/other",
                base
            )
        )
        assertEquals(
            null,
            BackendEndpointPolicy.parse(
                "https://user:password@project.supabase.co/functions/v1/deepseek-proxy",
                base
            )
        )
    }

    @Test
    fun localEndpointPolicyAllowsOnlyLoopbackChatEndpoint() {
        assertEquals(
            "http://127.0.0.1:5000/v1/chat/completions".toHttpUrl(),
            LocalInferenceEndpointPolicy.parse("http://127.0.0.1:5000/v1/chat/completions")
        )
        assertEquals(
            null,
            LocalInferenceEndpointPolicy.parse("http://192.168.1.20:5000/v1/chat/completions")
        )
        assertEquals(
            null,
            LocalInferenceEndpointPolicy.parse("https://api.example.com/v1/chat/completions")
        )
        assertEquals(
            null,
            LocalInferenceEndpointPolicy.parse("http://localhost:5000/admin")
        )
    }

    @Test
    fun remoteProviderUsesSupabaseSessionAndReturnsProxyReply() = runBlocking {
        val transport = FakeTransport(
            TransportResponse(
                statusCode = 200,
                body = """{"reply":"Hello from the server","model":"deepseek-flash"}"""
            )
        )
        val provider = DeepSeekProxyProvider(
            proxyUrl = "https://project.supabase.co/functions/v1/deepseek-proxy",
            supabaseUrl = "https://project.supabase.co",
            publishableKey = "sb_publishable_public_test",
            tokenProvider = FixedTokenProvider("user-session-jwt"),
            transport = transport
        )

        val result = provider.complete(userRequest())

        assertTrue(result is ChatProviderResult.Success)
        assertEquals("Hello from the server", (result as ChatProviderResult.Success).response.content)
        assertEquals("project.supabase.co", transport.url?.host)
        assertEquals("Bearer user-session-jwt", transport.headers["Authorization"])
        assertEquals("sb_publishable_public_test", transport.headers["apikey"])
        assertTrue(transport.body.contains("\"role\":\"user\""))
        assertFalse(transport.body.contains("DEEPSEEK_API_KEY"))
    }

    @Test
    fun remoteProviderFailsClosedWithoutUserSession() = runBlocking {
        val transport = FakeTransport(TransportResponse(200, """{"reply":"unused","model":"deepseek-flash"}"""))
        val provider = DeepSeekProxyProvider(
            "https://project.supabase.co/functions/v1/deepseek-proxy",
            "https://project.supabase.co",
            "sb_publishable_public_test",
            FixedTokenProvider(null),
            transport
        )

        val result = provider.complete(userRequest())

        assertEquals(
            ProviderErrorCode.AUTHENTICATION_REQUIRED,
            (result as ChatProviderResult.Failure).error.code
        )
        assertEquals(null, transport.url)
    }

    @Test
    fun remoteProviderMapsRateLimitAndServerFailures() = runBlocking {
        val limited = providerFor(TransportResponse(429, "{}")).complete(userRequest())
        assertEquals(
            ProviderErrorCode.RATE_LIMITED,
            (limited as ChatProviderResult.Failure).error.code
        )

        val unavailable = providerFor(TransportResponse(503, "{}")).complete(userRequest())
        assertEquals(
            ProviderErrorCode.SERVICE_UNAVAILABLE,
            (unavailable as ChatProviderResult.Failure).error.code
        )
    }

    @Test
    fun remoteProviderRejectsMalformedRequestsAndResponses() = runBlocking {
        val transport = FakeTransport(TransportResponse(200, """{"reply":"","model":"deepseek-flash"}"""))
        val provider = DeepSeekProxyProvider(
            "https://project.supabase.co/functions/v1/deepseek-proxy",
            "https://project.supabase.co",
            "sb_publishable_public_test",
            FixedTokenProvider("jwt"),
            transport
        )

        val invalidRequest = provider.complete(ChatRequest(emptyList()))
        assertEquals(
            ProviderErrorCode.INVALID_REQUEST,
            (invalidRequest as ChatProviderResult.Failure).error.code
        )

        val invalidResponse = provider.complete(userRequest())
        assertEquals(
            ProviderErrorCode.INVALID_RESPONSE,
            (invalidResponse as ChatProviderResult.Failure).error.code
        )
    }

    @Test
    fun localProviderUsesLoopbackWithoutCloudCredentials() = runBlocking {
        val transport = FakeTransport(
            TransportResponse(
                200,
                """{"model":"qwen-local","choices":[{"message":{"content":"Offline reply"}}]}"""
            )
        )
        val provider = LocalInferenceProvider(
            "http://127.0.0.1:5000/v1/chat/completions",
            transport,
            "qwen-local"
        )

        val result = provider.complete(userRequest())

        assertTrue(result is ChatProviderResult.Success)
        assertEquals("Offline reply", (result as ChatProviderResult.Success).response.content)
        assertTrue(transport.headers.isEmpty())
        assertEquals("127.0.0.1", transport.url?.host)
    }

    @Test
    fun routerDoesNotSilentlySendLocalModeToRemoteProvider() = runBlocking {
        val remote = CountingProvider(ChatProviderResult.Success(ChatResponse("remote", "deepseek-flash")))
        val router = ProviderRouter(remote, null)

        val result = router.complete(userRequest(), ProviderMode.LOCAL)

        assertEquals(
            ProviderErrorCode.LOCAL_MODEL_UNAVAILABLE,
            (result as ChatProviderResult.Failure).error.code
        )
        assertEquals(0, remote.calls)
    }

    private fun providerFor(response: TransportResponse) = DeepSeekProxyProvider(
        "https://project.supabase.co/functions/v1/deepseek-proxy",
        "https://project.supabase.co",
        "sb_publishable_public_test",
        FixedTokenProvider("jwt"),
        FakeTransport(response)
    )

    private fun userRequest() = ChatRequest(
        listOf(ChatMessage(ChatRole.USER, "Hello"))
    )

    private class FixedTokenProvider(private val token: String?) : AccessTokenProvider {
        override suspend fun accessToken(): String? = token
    }

    private class FakeTransport(
        private val response: TransportResponse
    ) : HttpPostTransport {
        var url: HttpUrl? = null
        var headers: Map<String, String> = emptyMap()
        var body: String = ""

        override suspend fun post(
            url: HttpUrl,
            headers: Map<String, String>,
            jsonBody: String
        ): TransportResponse {
            this.url = url
            this.headers = headers
            this.body = jsonBody
            return response
        }
    }

    private class CountingProvider(
        private val result: ChatProviderResult
    ) : ChatProvider {
        var calls = 0
        override suspend fun complete(request: ChatRequest): ChatProviderResult {
            calls += 1
            return result
        }
    }
}
