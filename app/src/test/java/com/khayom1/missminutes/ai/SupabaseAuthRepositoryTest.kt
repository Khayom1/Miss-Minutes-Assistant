package com.khayom1.missminutes.ai

import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SupabaseAuthRepositoryTest {
    private class FakeTransport(
        var response: AiHttpResponse
    ) : AiHttpTransport {
        var url: String? = null
        var headers: Map<String, String> = emptyMap()
        var body: String? = null
        var calls = 0

        override suspend fun post(
            url: String,
            headers: Map<String, String>,
            body: String
        ): AiHttpResponse {
            this.url = url
            this.headers = headers
            this.body = body
            calls++
            return response
        }
    }

    private fun repository(transport: FakeTransport) = DefaultSupabaseAuthRepository(
        projectUrl = "https://project.example",
        publishableKey = "sb_publishable_public-test-key",
        transport = transport,
        nowEpochSeconds = { 1_000L }
    )

    private val sessionJson = """
        {
          "access_token": "user-access-token",
          "refresh_token": "user-refresh-token",
          "expires_in": 3600,
          "user": {
            "id": "test-user-id",
            "email": "user@example.test"
          }
        }
    """.trimIndent()

    @Test
    fun signInUsesOnlyPublishableKeyAndReturnsUserSession() = runBlocking {
        val transport = FakeTransport(AiHttpResponse(200, sessionJson))
        val session = repository(transport).signIn("user@example.test", "long-password")

        assertEquals("user-access-token", session.accessToken)
        assertEquals("user-refresh-token", session.refreshToken)
        assertEquals("test-user-id", session.userId)
        assertEquals("user@example.test", session.email)
        assertEquals(4_600L, session.expiresAtEpochSeconds)
        assertEquals(
            "https://project.example/auth/v1/token?grant_type=password",
            transport.url
        )
        assertEquals("sb_publishable_public-test-key", transport.headers["apikey"])
        assertFalse(transport.headers.containsKey("Authorization"))
        assertFalse(transport.body.orEmpty().contains("service_role"))
        assertEquals("user@example.test", Json.parseToJsonElement(transport.body!!).jsonObject["email"]!!.jsonPrimitive.content)
    }

    @Test
    fun signUpWithoutImmediateTokensReportsEmailConfirmation() = runBlocking {
        val transport = FakeTransport(
            AiHttpResponse(200, """{"user":{"id":"pending-user","email":"user@example.test"}}""")
        )
        val result = repository(transport).signUp("user@example.test", "long-password")

        assertTrue(result.needsEmailConfirmation)
        assertEquals(null, result.session)
        assertEquals("https://project.example/auth/v1/signup", transport.url)
    }

    @Test
    fun refreshSendsRefreshTokenInRequestBody() = runBlocking {
        val transport = FakeTransport(AiHttpResponse(200, sessionJson))
        val session = repository(transport).refresh("old-refresh-token")

        assertEquals("user-access-token", session.accessToken)
        assertEquals(
            "https://project.example/auth/v1/token?grant_type=refresh_token",
            transport.url
        )
        val body = Json.parseToJsonElement(transport.body!!).jsonObject
        assertEquals("old-refresh-token", body["refresh_token"]!!.jsonPrimitive.content)
    }

    @Test
    fun signOutSendsUserBearerTokenAndPublishableKey() = runBlocking {
        val transport = FakeTransport(AiHttpResponse(204, ""))
        repository(transport).signOut("signed-in-user-token")

        assertEquals("https://project.example/auth/v1/logout", transport.url)
        assertEquals("Bearer signed-in-user-token", transport.headers["Authorization"])
        assertEquals("sb_publishable_public-test-key", transport.headers["apikey"])
    }

    @Test
    fun invalidCredentialsAreMappedToSafeTypedError() = runBlocking {
        val transport = FakeTransport(AiHttpResponse(400, """{"msg":"internal provider text"}"""))
        val error = runCatching {
            repository(transport).signIn("user@example.test", "long-password")
        }.exceptionOrNull()

        assertTrue(error is SupabaseAuthException.InvalidCredentials)
        assertFalse(error?.message.orEmpty().contains("internal provider text"))
    }

    @Test
    fun signInAcceptsAnExistingShortPasswordWhileSignUpRequiresEightCharacters() = runBlocking {
        val transport = FakeTransport(AiHttpResponse(200, sessionJson))
        repository(transport).signIn("user@example.test", "short")
        assertEquals(1, transport.calls)

        val signUpTransport = FakeTransport(AiHttpResponse(200, sessionJson))
        val error = runCatching {
            repository(signUpTransport).signUp("user@example.test", "short")
        }.exceptionOrNull()
        assertTrue(error is IllegalArgumentException)
        assertEquals(0, signUpTransport.calls)
    }

    @Test
    fun invalidEmailAndShortPasswordAreRejectedBeforeNetwork() = runBlocking {
        val transport = FakeTransport(AiHttpResponse(200, sessionJson))
        val repo = repository(transport)

        assertTrue(runCatching { repo.signIn("not-an-email", "long-password") }.isFailure)
        assertTrue(runCatching { repo.signUp("user@example.test", "short") }.isFailure)
        assertEquals(0, transport.calls)
    }
}
