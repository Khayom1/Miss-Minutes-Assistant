package com.khayom1.missminutes.ai

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

interface SessionStore {
    fun load(): SupabaseAuthSession?
    fun save(session: SupabaseAuthSession)
    fun clear()
}

/**
 * Serializes token refreshes and stores only encrypted sessions via SessionStore.
 * The refresh token is never exposed to UI state.
 */
class SessionManager(
    private val repository: SupabaseAuthRepository,
    private val store: SessionStore,
    private val nowEpochSeconds: () -> Long = { System.currentTimeMillis() / 1_000L }
) : AccessTokenSource {
    private val mutex = Mutex()

    suspend fun signIn(email: String, password: String): SupabaseAuthSession =
        mutex.withLock {
            repository.signIn(email, password).also(store::save)
        }

    suspend fun signUp(email: String, password: String): AuthFlowResult =
        mutex.withLock {
            repository.signUp(email, password).also { result ->
                result.session?.let(store::save)
            }
        }

    suspend fun restoreSession(): SupabaseAuthSession? =
        mutex.withLock { refreshIfNeededLocked() }

    suspend fun currentSession(): SupabaseAuthSession? =
        mutex.withLock { store.load() }

    override suspend fun currentAccessToken(): String? =
        mutex.withLock { refreshIfNeededLocked()?.accessToken }

    suspend fun signOut() = mutex.withLock {
        val current = store.load()
        try {
            if (current != null) repository.signOut(current.accessToken)
        } finally {
            store.clear()
        }
    }

    private suspend fun refreshIfNeededLocked(): SupabaseAuthSession? {
        val current = store.load() ?: return null
        if (current.expiresAtEpochSeconds > nowEpochSeconds() + REFRESH_SKEW_SECONDS) {
            return current
        }

        return try {
            repository.refresh(current.refreshToken).also(store::save)
        } catch (error: SupabaseAuthException.SessionExpired) {
            store.clear()
            null
        } catch (error: SupabaseAuthException.InvalidCredentials) {
            store.clear()
            null
        }
    }

    private companion object {
        const val REFRESH_SKEW_SECONDS = 60L
    }
}
