package com.khayom1.missminutes.ai

import java.io.IOException
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json

data class SupabaseAuthSession(
    val accessToken: String,
    val refreshToken: String,
    val expiresAtEpochSeconds: Long,
    val userId: String,
    val email: String?
)

data class AuthFlowResult(
    val session: SupabaseAuthSession?,
    val needsEmailConfirmation: Boolean
)

sealed class SupabaseAuthException(message: String, cause: Throwable? = null) :
    Exception(message, cause) {
    class InvalidCredentials : SupabaseAuthException("Email or password is invalid.")
    class SessionExpired : SupabaseAuthException("The sign-in session has expired.")
    class RateLimited : SupabaseAuthException("Too many authentication attempts. Try again later.")
    class Unavailable(cause: Throwable? = null) :
        SupabaseAuthException("Authentication service is unavailable.", cause)
    class InvalidResponse(cause: Throwable? = null) :
        SupabaseAuthException("Authentication service returned an invalid response.", cause)
}

interface SupabaseAuthRepository {
    suspend fun signIn(email: String, password: String): SupabaseAuthSession
    suspend fun signUp(email: String, password: String): AuthFlowResult
    suspend fun refresh(refreshToken: String): SupabaseAuthSession
    suspend fun signOut(accessToken: String)
}

@Serializable
private data class CredentialsBody(
    val email: String,
    val password: String
)

@Serializable
private data class RefreshBody(
    @SerialName("refresh_token") val refreshToken: String
)

@Serializable
private data class AuthApiUser(
    val id: String? = null,
    val email: String? = null
)

@Serializable
private data class AuthApiResponse(
    @SerialName("access_token") val accessToken: String? = null,
    @SerialName("refresh_token") val refreshToken: String? = null,
    @SerialName("expires_in") val expiresIn: Long? = null,
    val user: AuthApiUser? = null
)

/**
 * Uses the public Supabase Auth API. Only the public publishable key and
 * user-issued session tokens are sent by the app; server keys are never used.
 */
class DefaultSupabaseAuthRepository(
    private val projectUrl: String,
    private val publishableKey: String,
    private val transport: AiHttpTransport,
    private val json: Json = Json { ignoreUnknownKeys = true; explicitNulls = false },
    private val nowEpochSeconds: () -> Long = { System.currentTimeMillis() / 1_000L }
) : SupabaseAuthRepository {
    init {
        require(projectUrl.startsWith("https://")) { "Supabase URL must use HTTPS." }
        require(publishableKey.isNotBlank()) { "Supabase publishable key is required." }
    }

    override suspend fun signIn(email: String, password: String): SupabaseAuthSession {
        validateCredentials(email, password)
        val body = encode(CredentialsBody(email.trim(), password))
        val response = post("/auth/v1/token?grant_type=password", body)
        return parseSession(response) ?: throw SupabaseAuthException.InvalidResponse()
    }

    override suspend fun signUp(email: String, password: String): AuthFlowResult {
        validateCredentials(email, password)
        val body = encode(CredentialsBody(email.trim(), password))
        val response = post("/auth/v1/signup", body)
        val session = parseSession(response)
        return AuthFlowResult(
            session = session,
            needsEmailConfirmation = session == null
        )
    }

    override suspend fun refresh(refreshToken: String): SupabaseAuthSession {
        if (refreshToken.isBlank()) throw SupabaseAuthException.SessionExpired()
        val body = encode(RefreshBody(refreshToken))
        val response = post("/auth/v1/token?grant_type=refresh_token", body)
        return parseSession(response) ?: throw SupabaseAuthException.SessionExpired()
    }

    override suspend fun signOut(accessToken: String) {
        if (accessToken.isBlank()) return
        try {
            val response = transport.post(
                url = endpoint("/auth/v1/logout"),
                headers = mapOf(
                    "apikey" to publishableKey,
                    "Authorization" to "Bearer $accessToken",
                    "Content-Type" to "application/json"
                ),
                body = "{}"
            )
            if (response.statusCode !in 200..299 && response.statusCode != 401) {
                throw SupabaseAuthException.Unavailable()
            }
        } catch (error: IOException) {
            throw SupabaseAuthException.Unavailable(error)
        }
    }

    private suspend fun post(path: String, body: String): String {
        val response = try {
            transport.post(
                url = endpoint(path),
                headers = mapOf(
                    "apikey" to publishableKey,
                    "Content-Type" to "application/json"
                ),
                body = body
            )
        } catch (error: IOException) {
            throw SupabaseAuthException.Unavailable(error)
        }

        when (response.statusCode) {
            in 200..299 -> return response.body
            400, 401, 403 -> throw SupabaseAuthException.InvalidCredentials()
            429 -> throw SupabaseAuthException.RateLimited()
            in 500..599 -> throw SupabaseAuthException.Unavailable()
            else -> throw SupabaseAuthException.InvalidResponse()
        }
    }

    private fun parseSession(body: String): SupabaseAuthSession? {
        val response = try {
            json.decodeFromString(AuthApiResponse.serializer(), body)
        } catch (error: SerializationException) {
            throw SupabaseAuthException.InvalidResponse(error)
        }

        val access = response.accessToken?.takeIf { it.isNotBlank() } ?: return null
        val refresh = response.refreshToken?.takeIf { it.isNotBlank() }
            ?: throw SupabaseAuthException.InvalidResponse()
        val userId = response.user?.id?.takeIf { it.isNotBlank() }
            ?: throw SupabaseAuthException.InvalidResponse()
        val expiresIn = response.expiresIn?.takeIf { it > 0 }
            ?: throw SupabaseAuthException.InvalidResponse()

        return SupabaseAuthSession(
            accessToken = access,
            refreshToken = refresh,
            expiresAtEpochSeconds = nowEpochSeconds() + expiresIn,
            userId = userId,
            email = response.user.email
        )
    }

    private fun encode(value: CredentialsBody): String =
        json.encodeToString(CredentialsBody.serializer(), value)

    private fun encode(value: RefreshBody): String =
        json.encodeToString(RefreshBody.serializer(), value)

    private fun endpoint(path: String): String =
        projectUrl.trimEnd('/') + path

    private fun validateCredentials(email: String, password: String) {
        val normalizedEmail = email.trim()
        require(normalizedEmail.length in 3..254 && normalizedEmail.contains('@')) {
            "Enter a valid email address."
        }
        require(password.length in 8..128) {
            "Password must contain 8–128 characters."
        }
    }
}
