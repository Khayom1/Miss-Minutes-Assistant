package com.khayom1.missminutes.auth

import com.khayom1.missminutes.ai.HttpPostTransport
import com.khayom1.missminutes.ai.TransportResponse
import kotlinx.coroutines.runBlocking
import okhttp3.HttpUrl
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SupabaseAuthRepositoryTest {

    @Test
    fun signInStoresOnlyTheReturnedUserSessionInMemory() = runBlocking {
        val transport = FakeTransport(
            TransportResponse(
                200,
                """{"access_token":"user-access","refresh_token":"user-refresh","expires_in":3600,"user":{"email":"user@example.com"}}"""
            )
        )
        val store = InMemorySessionStore(nowEpochSeconds = { 1_000L })
        val repository = repository(transport, store)

        val result = repository.signIn(" user@example.com ", "strong-password")

        assertTrue(result is AuthResult.SignedIn)
        assertEquals("user-access", store.currentSession?.accessToken)
        assertEquals("user@example.com", store.currentSession?.email)
        assertEquals(4_600L, store.currentSession?.expiresAtEpochSeconds)
        assertEquals("sb_publishable_public_test", transport.headers["apikey"])
        assertEquals("/auth/v1/token", transport.url?.encodedPath)
        assertEquals("password", transport.url?.queryParameter("grant_type"))
    }

    @Test
    fun signUpCanReportEmailConfirmationWithoutCreatingAnInMemorySession() = runBlocking {
        val transport = FakeTransport(
            TransportResponse(200, """{"user":{"email":"new@example.com"}}""")
        )
        val store = InMemorySessionStore(nowEpochSeconds = { 1_000L })
        val repository = repository(transport, store)

        val result = repository.signUp("new@example.com", "strong-password")

        assertEquals(AuthResult.NeedsEmailConfirmation, result)
        assertNull(store.currentSession)
        assertEquals("/auth/v1/signup", transport.url?.encodedPath)
    }

    @Test
    fun invalidInputNeverMakesANetworkRequest() = runBlocking {
        val transport = FakeTransport(TransportResponse(200, "{}"))
        val repository = repository(transport, InMemorySessionStore())

        val result = repository.signIn("not-an-email", "short")

        assertEquals(AuthResult.Failure(AuthErrorCode.INVALID_INPUT), result)
        assertNull(transport.url)
    }

    @Test
    fun signOutClearsTheSessionEvenWhenTheServerRejectsTheRequest() = runBlocking {
        val store = InMemorySessionStore(nowEpochSeconds = { 1_000L })
        store.save(
            AuthSession(
                accessToken = "access",
                refreshToken = "refresh",
                email = "user@example.com",
                expiresAtEpochSeconds = 5_000L
            )
        )
        val transport = FakeTransport(TransportResponse(401, "{}"))
        val repository = repository(transport, store)

        val result = repository.signOut()

        assertEquals(AuthResult.Failure(AuthErrorCode.NETWORK), result)
        assertNull(store.currentSession)
        assertEquals("Bearer access", transport.headers["Authorization"])
    }

    @Test
    fun expiredSessionsAreNotExposedAsBearerTokens() = runBlocking {
        val store = InMemorySessionStore(nowEpochSeconds = { 2_000L })
        store.save(
            AuthSession(
                accessToken = "expired",
                refreshToken = "refresh",
                email = "user@example.com",
                expiresAtEpochSeconds = 2_010L
            )
        )

        assertNull(store.accessToken())
        assertNull(store.currentSession)
    }

    private fun repository(transport: FakeTransport, store: SessionStore) =
        SupabaseAuthRepository(
            supabaseUrl = "https://project.supabase.co",
            publishableKey = "sb_publishable_public_test",
            transport = transport,
            sessionStore = store,
            nowEpochSeconds = { 1_000L }
        )

    private class FakeTransport(
        private val response: TransportResponse
    ) : HttpPostTransport {
        var url: HttpUrl? = null
        var headers: Map<String, String> = emptyMap()

        override suspend fun post(
            url: HttpUrl,
            headers: Map<String, String>,
            jsonBody: String
        ): TransportResponse {
            this.url = url
            this.headers = headers
            return response
        }
    }
}
