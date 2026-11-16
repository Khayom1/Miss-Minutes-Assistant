package com.khayom1.missminutes.auth

import com.khayom1.missminutes.ai.HttpPostTransport
import com.khayom1.missminutes.ai.SupabaseEndpointPolicy
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

class SupabaseAuthRepository(
    private val supabaseUrl: String,
    private val publishableKey: String,
    private val transport: HttpPostTransport,
    private val sessionStore: SessionStore
) : AuthRepository {

    override suspend fun signIn(email: String, password: String): AuthResult =
        authenticate(email, password, signUp = false)

    override suspend fun signUp(email: String, password: String): AuthResult =
        authenticate(email, password, signUp = true)

    override suspend fun signOut(): AuthResult {
        val session = sessionStore.currentSession ?: return AuthResult.NeedsEmailConfirmation
        val base = SupabaseEndpointPolicy.parse(supabaseUrl)
            ?: return AuthResult.Failure(AuthErrorCode.NOT_CONFIGURED)
        val endpoint = base.newBuilder().encodedPath("/auth/v1/logout").build()

        val response = try {
            transport.post(
                endpoint,
                mapOf(
                    "apikey" to publishableKey,
                    "Authorization" to "Bearer ${session.accessToken}"
                ),
                "{}"
            )
        } catch (_: Exception) {
            sessionStore.clear()
            return AuthResult.Failure(AuthErrorCode.NETWORK)
        }

        sessionStore.clear()
        return if (response.failure == null && response.statusCode in 200..299) {
            AuthResult.NeedsEmailConfirmation
        } else {
            AuthResult.Failure(AuthErrorCode.NETWORK)
        }
    }

    private suspend fun authenticate(
        email: String,
        password: String,
        signUp: Boolean
    ): AuthResult {
        val normalizedEmail = email.trim()
        if (!EMAIL_REGEX.matches(normalizedEmail) || password.length < 8) {
            return AuthResult.Failure(AuthErrorCode.INVALID_INPUT)
        }
        if (publishableKey.isBlank()) return AuthResult.Failure(AuthErrorCode.NOT_CONFIGURED)

        val base = SupabaseEndpointPolicy.parse(supabaseUrl)
            ?: return AuthResult.Failure(AuthErrorCode.NOT_CONFIGURED)
        val path = if (signUp) "/auth/v1/signup" else "/auth/v1/token"
        val endpointBuilder = base.newBuilder().encodedPath(path)
        if (!signUp) endpointBuilder.addQueryParameter("grant_type", "password")
        val endpoint = endpointBuilder.build()

        val body = try {
            JSON.encodeToString(AuthRequestDto(normalizedEmail, password))
        } catch (_: Exception) {
            return AuthResult.Failure(AuthErrorCode.INVALID_INPUT)
        }

        val response = try {
            transport.post(endpoint, mapOf("apikey" to publishableKey), body)
        } catch (_: Exception) {
            return AuthResult.Failure(AuthErrorCode.NETWORK)
        }

        if (response.failure != null) return AuthResult.Failure(AuthErrorCode.NETWORK)
        if (response.statusCode == 400 || response.statusCode == 401 || response.statusCode == 422) {
            return AuthResult.Failure(AuthErrorCode.INVALID_CREDENTIALS)
        }
        if (response.statusCode !in 200..299) {
            return AuthResult.Failure(AuthErrorCode.SERVICE_UNAVAILABLE)
        }

        val decoded = try {
            JSON.decodeFromString<AuthResponseDto>(response.body.orEmpty())
        } catch (_: Exception) {
            return AuthResult.Failure(AuthErrorCode.INVALID_RESPONSE)
        }

        val accessToken = decoded.accessToken?.takeIf { it.isNotBlank() }
        val refreshToken = decoded.refreshToken?.takeIf { it.isNotBlank() }
        if (accessToken == null || refreshToken == null) {
            return if (signUp && decoded.user?.email != null) {
                AuthResult.NeedsEmailConfirmation
            } else {
                AuthResult.Failure(AuthErrorCode.INVALID_RESPONSE)
            }
        }

        val session = AuthSession(
            accessToken = accessToken,
            refreshToken = refreshToken,
            email = decoded.user?.email ?: normalizedEmail
        )
        sessionStore.save(session)
        return AuthResult.SignedIn(session)
    }

    private companion object {
        val JSON = Json { ignoreUnknownKeys = true }
        val EMAIL_REGEX = Regex("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$")
    }
}

@Serializable
private data class AuthRequestDto(
    val email: String,
    val password: String
)

@Serializable
private data class AuthResponseDto(
    @SerialName("access_token") val accessToken: String? = null,
    @SerialName("refresh_token") val refreshToken: String? = null,
    val user: AuthUserDto? = null
)

@Serializable
private data class AuthUserDto(
    val email: String? = null
)
