package com.khayom1.missminutes.ai

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.InterruptedIOException
import java.net.SocketTimeoutException

class LocalInferenceProvider(
    private val endpointUrl: String,
    private val transport: HttpPostTransport,
    private val model: String = "local"
) : ChatProvider {

    override suspend fun complete(request: ChatRequest): ChatProviderResult {
        if (!request.isValid()) return failure(ProviderErrorCode.INVALID_REQUEST)
        val endpoint = LocalInferenceEndpointPolicy.parse(endpointUrl)
            ?: return failure(ProviderErrorCode.LOCAL_MODEL_UNAVAILABLE)

        val body = try {
            JSON.encodeToString(
                LocalChatRequestDto(
                    model = model,
                    messages = request.messages.map {
                        LocalMessageDto(role = it.role.wireValue, content = it.content)
                    }
                )
            )
        } catch (_: Exception) {
            return failure(ProviderErrorCode.INVALID_REQUEST)
        }

        val response = try {
            transport.post(endpoint, emptyMap(), body)
        } catch (error: Exception) {
            return failure(if (error.isTimeout()) ProviderErrorCode.TIMEOUT else ProviderErrorCode.NETWORK)
        }

        if (response.failure != null) {
            return failure(
                if (response.failure.isTimeout()) ProviderErrorCode.TIMEOUT
                else ProviderErrorCode.NETWORK
            )
        }

        if (response.statusCode !in 200..299) {
            return failure(ProviderErrorCode.LOCAL_MODEL_UNAVAILABLE)
        }

        val decoded = try {
            JSON.decodeFromString<LocalChatResponseDto>(response.body.orEmpty())
        } catch (_: Exception) {
            return failure(ProviderErrorCode.INVALID_RESPONSE)
        }

        val reply = decoded.choices.firstOrNull()?.message?.content
        if (reply.isNullOrBlank() || reply.length > MAX_REPLY_CHARS) {
            return failure(ProviderErrorCode.INVALID_RESPONSE)
        }

        return ChatProviderResult.Success(ChatResponse(reply, decoded.model ?: model))
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
private data class LocalChatRequestDto(
    val model: String,
    val messages: List<LocalMessageDto>,
    val stream: Boolean = false,
    @SerialName("max_tokens") val maxTokens: Int = 512
)

@Serializable
private data class LocalMessageDto(
    val role: String,
    val content: String
)

@Serializable
private data class LocalChatResponseDto(
    val model: String? = null,
    val choices: List<LocalChoiceDto> = emptyList()
)

@Serializable
private data class LocalChoiceDto(
    val message: LocalResponseMessageDto
)

@Serializable
private data class LocalResponseMessageDto(
    val content: String
)
