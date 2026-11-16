package com.khayom1.missminutes.ai

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import java.io.InterruptedIOException
import java.net.SocketTimeoutException

class DeepSeekProxyProvider(
    private val proxyUrl: String,
    private val supabaseUrl: String,
    private val publishableKey: String,
    private val tokenProvider: AccessTokenProvider,
    private val transport: HttpPostTransport
) : ChatProvider {

    override suspend fun complete(request: ChatRequest): ChatProviderResult {
        if (!request.isValid()) {
            return failure(ProviderErrorCode.INVALID_REQUEST)
        }

        val endpoint = BackendEndpointPolicy.parse(proxyUrl, supabaseUrl)
            ?: return failure(ProviderErrorCode.NOT_CONFIGURED)
        if (publishableKey.isBlank()) return failure(ProviderErrorCode.NOT_CONFIGURED)

        val accessToken = tokenProvider.accessToken()?.takeIf { it.isNotBlank() }
            ?: return failure(ProviderErrorCode.AUTHENTICATION_REQUIRED)

        val body = try {
            JSON.encodeToString(
                ProxyChatRequestDto(
                    messages = request.messages.map {
                        ProxyMessageDto(role = it.role.wireValue, content = it.content)
                    }
                )
            )
        } catch (_: Exception) {
            return failure(ProviderErrorCode.INVALID_REQUEST)
        }

        val response = try {
            transport.post(
                endpoint,
                mapOf(
                    "apikey" to publishableKey,
                    "Authorization" to "Bearer $accessToken"
                ),
                body
            )
        } catch (error: Exception) {
            return failure(if (error.isTimeout()) ProviderErrorCode.TIMEOUT else ProviderErrorCode.NETWORK)
        }

        if (response.failure != null) {
            return failure(
                if (response.failure.isTimeout()) ProviderErrorCode.TIMEOUT
                else ProviderErrorCode.NETWORK
            )
        }

        when (response.statusCode) {
            401, 403 -> return failure(ProviderErrorCode.AUTHENTICATION_REQUIRED)
            429 -> return failure(ProviderErrorCode.RATE_LIMITED)
            in 500..599 -> return failure(ProviderErrorCode.SERVICE_UNAVAILABLE)
            in 200..299 -> Unit
            else -> return failure(ProviderErrorCode.INVALID_RESPONSE)
        }

        val decoded = try {
            JSON.decodeFromString<ProxyChatResponseDto>(response.body.orEmpty())
        } catch (_: Exception) {
            return failure(ProviderErrorCode.INVALID_RESPONSE)
        }

        if (decoded.reply.isBlank() || decoded.reply.length > MAX_REPLY_CHARS || decoded.model.isBlank()) {
            return failure(ProviderErrorCode.INVALID_RESPONSE)
        }

        return ChatProviderResult.Success(
            ChatResponse(content = decoded.reply, model = decoded.model)
        )
    }

    private fun failure(code: ProviderErrorCode) =
        ChatProviderResult.Failure(ProviderError(code))

    private fun Throwable.isTimeout(): Boolean =
        this is SocketTimeoutException || this is InterruptedIOException

    private companion object {
        const val MAX_REPLY_CHARS = 16_000
        val JSON = Json { ignoreUnknownKeys = true }
    }
}

@Serializable
private data class ProxyChatRequestDto(
    val messages: List<ProxyMessageDto>
)

@Serializable
private data class ProxyMessageDto(
    val role: String,
    val content: String
)

@Serializable
private data class ProxyChatResponseDto(
    val reply: String,
    val model: String
)
