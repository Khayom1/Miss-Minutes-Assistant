package com.khayom1.missminutes.auth

import com.khayom1.missminutes.ai.AccessTokenProvider

data class AuthSession(
    val accessToken: String,
    val refreshToken: String,
    val email: String?
)

enum class AuthErrorCode {
    NOT_CONFIGURED,
    INVALID_INPUT,
    INVALID_CREDENTIALS,
    NETWORK,
    SERVICE_UNAVAILABLE,
    INVALID_RESPONSE
}

sealed interface AuthResult {
    data class SignedIn(val session: AuthSession) : AuthResult
    data object NeedsEmailConfirmation : AuthResult
    data class Failure(val error: AuthErrorCode) : AuthResult
}

interface AuthRepository {
    suspend fun signIn(email: String, password: String): AuthResult
    suspend fun signUp(email: String, password: String): AuthResult
    suspend fun signOut(): AuthResult
}

interface SessionStore : AccessTokenProvider {
    val currentSession: AuthSession?
    fun save(session: AuthSession)
    fun clear()
}

class InMemorySessionStore : SessionStore {
    @Volatile
    private var session: AuthSession? = null

    override val currentSession: AuthSession?
        get() = session

    override suspend fun accessToken(): String? = session?.accessToken

    override fun save(session: AuthSession) {
        this.session = session
    }

    override fun clear() {
        session = null
    }
}
