package com.khayom1.missminutes.ai

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SessionManagerTest {
    private class MemoryStore : SessionStore {
        var value: SupabaseAuthSession? = null
        var clearCalls = 0

        override fun load(): SupabaseAuthSession? = value
        override fun save(session: SupabaseAuthSession) {
            value = session
        }
        override fun clear() {
            value = null
            clearCalls++
        }
    }

    private class FakeRepository : SupabaseAuthRepository {
        var refreshCalls = 0
        var signOutCalls = 0
        var refreshResult = SupabaseAuthSession(
            accessToken = "fresh-access",
            refreshToken = "fresh-refresh",
            expiresAtEpochSeconds = 5_000L,
            userId = "user-1",
            email = "user@example.test"
        )
        var refreshError: Throwable? = null
        var signOutError: Throwable? = null

        override suspend fun signIn(email: String, password: String) = refreshResult
        override suspend fun signUp(email: String, password: String) =
            AuthFlowResult(session = null, needsEmailConfirmation = true)
        override suspend fun refresh(refreshToken: String): SupabaseAuthSession {
            refreshCalls++
            refreshError?.let { throw it }
            return refreshResult
        }
        override suspend fun signOut(accessToken: String) {
            signOutCalls++
            signOutError?.let { throw it }
        }
    }

    private fun session(expiresAt: Long) = SupabaseAuthSession(
        accessToken = "stale-access",
        refreshToken = "refresh-token",
        expiresAtEpochSeconds = expiresAt,
        userId = "user-1",
        email = "user@example.test"
    )

    @Test
    fun validSessionDoesNotRefresh() = runBlocking {
        val store = MemoryStore().apply { value = session(5_000L) }
        val repository = FakeRepository()
        val manager = SessionManager(repository, store, nowEpochSeconds = { 1_000L })

        assertEquals("stale-access", manager.currentAccessToken())
        assertEquals(0, repository.refreshCalls)
    }

    @Test
    fun expiredSessionRefreshesAndStoresNewTokens() = runBlocking {
        val store = MemoryStore().apply { value = session(1_010L) }
        val repository = FakeRepository()
        val manager = SessionManager(repository, store, nowEpochSeconds = { 1_000L })

        assertEquals("fresh-access", manager.currentAccessToken())
        assertEquals("fresh-refresh", store.value?.refreshToken)
        assertEquals(1, repository.refreshCalls)

        assertEquals("fresh-access", manager.currentAccessToken())
        assertEquals(1, repository.refreshCalls)
    }

    @Test
    fun invalidRefreshSessionIsCleared() = runBlocking {
        val store = MemoryStore().apply { value = session(1_010L) }
        val repository = FakeRepository().apply {
            refreshError = SupabaseAuthException.SessionExpired()
        }
        val manager = SessionManager(repository, store, nowEpochSeconds = { 1_000L })

        assertNull(manager.currentAccessToken())
        assertNull(store.value)
        assertEquals(1, store.clearCalls)
    }

    @Test
    fun networkRefreshFailurePreservesSessionForRetry() = runBlocking {
        val original = session(1_010L)
        val store = MemoryStore().apply { value = original }
        val repository = FakeRepository().apply {
            refreshError = SupabaseAuthException.Unavailable()
        }
        val manager = SessionManager(repository, store, nowEpochSeconds = { 1_000L })

        val error = runCatching { manager.currentAccessToken() }.exceptionOrNull()
        assertTrue(error is SupabaseAuthException.Unavailable)
        assertEquals(original, store.value)
        assertEquals(0, store.clearCalls)
    }

    @Test
    fun signOutClearsTokensEvenWhenRemoteRevocationFails() = runBlocking {
        val store = MemoryStore().apply { value = session(5_000L) }
        val repository = FakeRepository().apply {
            signOutError = SupabaseAuthException.Unavailable()
        }
        val manager = SessionManager(repository, store)

        val error = runCatching { manager.signOut() }.exceptionOrNull()
        assertTrue(error is SupabaseAuthException.Unavailable)
        assertNull(store.value)
        assertEquals(1, store.clearCalls)
        assertEquals(1, repository.signOutCalls)
    }

    @Test
    fun sessionStringRedactsTokensAndIdentity() {
        val value = SupabaseAuthSession(
            accessToken = "private-access-token",
            refreshToken = "private-refresh-token",
            expiresAtEpochSeconds = 5_000L,
            userId = "private-user-id",
            email = "private@example.test"
        ).toString()

        assertFalse(value.contains("private-access-token"))
        assertFalse(value.contains("private-refresh-token"))
        assertFalse(value.contains("private-user-id"))
        assertFalse(value.contains("private@example.test"))
        assertTrue(value.contains("<redacted>"))
    }

    @Test
    fun emailConfirmationSignUpDoesNotPersistAnEmptySession() = runBlocking {
        val store = MemoryStore()
        val manager = SessionManager(FakeRepository(), store)

        val result = manager.signUp("user@example.test", "long-password")
        assertTrue(result.needsEmailConfirmation)
        assertNull(store.value)
    }
}
