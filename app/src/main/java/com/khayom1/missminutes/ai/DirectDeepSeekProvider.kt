package com.khayom1.missminutes.ai

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json

fun interface DeepSeekApiKeySource {
    fun currentApiKey(): String?
}

@Serializable
private data class DeepSeekRequest(
    val model: String,
    val messages: List<DeepSeekWireMessage>,
    @SerialName("max_tokens") val maxTokens: Int,
    val stream: Boolean = false
)

@Serializable
private data class DeepSeekWireMessage(val role: String, val content: String)

@Serializable
private data class DeepSeekResponse(
    val model: String? = null,
    val choices: List<DeepSeekChoice> = emptyList()
)

@Serializable
private data class DeepSeekChoice(val message: DeepSeekAssistantMessage? = null)

@Serializable
private data class DeepSeekAssistantMessage(val content: String? = null)

/** Calls DeepSeek directly from the device with the user's own API key. No proxy or Supabase AI function. */
class DirectDeepSeekProvider(
    private val keySource: DeepSeekApiKeySource,
    private val transport: AiHttpTransport,
    private val json: Json = Json { ignoreUnknownKeys = true },
    private val model: String = "deepseek-chat"
) : AiProvider {
    override suspend fun complete(request: AiRequest): AiCompletion {
        val key = keySource.currentApiKey()?.takeIf { it.isNotBlank() }
            ?: throw AiProviderException.AuthenticationRequired()
        val payload = try {
            json.encodeToString(
                DeepSeekRequest.serializer(),
                DeepSeekRequest(
                    model = model,
                    messages = request.messages.map { DeepSeekWireMessage(it.role.wireValue, it.content) },
                    maxTokens = request.maxOutputTokens
                )
            )
        } catch (error: SerializationException) {
            throw AiProviderException.InvalidResponse(error)
        }
        val response = transport.post(
            url = "https://api.deepseek.com/chat/completions",
            headers = mapOf(
                "Authorization" to "Bearer $key",
                "Content-Type" to "application/json"
            ),
            body = payload
        )
        when (response.statusCode) {
            200 -> Unit
            401, 403 -> throw AiProviderException.AuthenticationRequired()
            429 -> throw AiProviderException.RateLimited()
            in 500..599 -> throw AiProviderException.Unavailable()
            else -> throw AiProviderException.InvalidResponse()
        }
        val decoded = try {
            json.decodeFromString(DeepSeekResponse.serializer(), response.body)
        } catch (error: SerializationException) {
            throw AiProviderException.InvalidResponse(error)
        }
        val content = decoded.choices.firstOrNull()?.message?.content?.trim()
            ?.takeIf { it.isNotEmpty() && it.length <= 64_000 }
            ?: throw AiProviderException.InvalidResponse()
        return AiCompletion(content, decoded.model?.takeIf { it.isNotBlank() } ?: model)
    }
}
