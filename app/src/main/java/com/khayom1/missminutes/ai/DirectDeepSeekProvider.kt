package com.khayom1.missminutes.ai

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import java.io.IOException

fun interface DeepSeekApiKeySource {
    suspend fun currentApiKey(): String?
}

@Serializable
private data class DeepSeekRequest(
    val model: String,
    val messages: List<DeepSeekWireMessage>,
    @SerialName("max_tokens") val maxTokens: Int,
    val stream: Boolean = false,
    val tools: List<DeepSeekTool>? = null,
    @SerialName("tool_choice") val toolChoice: String? = null
)

@Serializable
private data class DeepSeekWireMessage(
    val role: String,
    val content: String? = null,
    @SerialName("tool_calls") val toolCalls: List<DeepSeekToolCall>? = null,
    @SerialName("tool_call_id") val toolCallId: String? = null,
    val name: String? = null
)

@Serializable
private data class DeepSeekTool(
    val type: String,
    val function: DeepSeekFunctionDefinition
)

@Serializable
private data class DeepSeekFunctionDefinition(
    val name: String,
    val description: String,
    val parameters: JsonObject
)

@Serializable
private data class DeepSeekToolCall(
    val id: String,
    val type: String,
    val function: DeepSeekFunctionCall
)

@Serializable
private data class DeepSeekFunctionCall(
    val name: String,
    val arguments: String
)

@Serializable
private data class DeepSeekResponse(
    val model: String? = null,
    val choices: List<DeepSeekChoice> = emptyList()
)

@Serializable
private data class DeepSeekChoice(val message: DeepSeekAssistantMessage? = null)

@Serializable
private data class DeepSeekAssistantMessage(
    val content: String? = null,
    @SerialName("tool_calls") val toolCalls: List<DeepSeekToolCall> = emptyList()
)

/** Calls DeepSeek directly from the device with the user's own API key. No proxy or Supabase AI function. */
class DirectDeepSeekProvider(
    private val keySource: DeepSeekApiKeySource,
    private val transport: AiHttpTransport,
    private val json: Json = Json { ignoreUnknownKeys = true; explicitNulls = false },
    private val model: String = "deepseek-chat"
) : AiProvider {
    override suspend fun complete(request: AiRequest): AiCompletion {
        val key = withContext(Dispatchers.IO) { keySource.currentApiKey() }
            ?.takeIf { it.isNotBlank() }
            ?: throw AiProviderException.MissingApiKey()

        val payload = try {
            json.encodeToString(
                DeepSeekRequest.serializer(),
                DeepSeekRequest(
                    model = model,
                    messages = request.messages.map { message ->
                        DeepSeekWireMessage(
                            role = message.role.wireValue,
                            content = message.content.takeIf { it.isNotBlank() },
                            toolCalls = message.toolCalls.takeIf { it.isNotEmpty() }?.map { call ->
                                DeepSeekToolCall(
                                    id = call.id,
                                    type = "function",
                                    function = DeepSeekFunctionCall(call.name, call.arguments)
                                )
                            },
                            toolCallId = message.toolCallId,
                            name = message.name
                        )
                    },
                    maxTokens = request.maxOutputTokens,
                    tools = request.tools.takeIf { it.isNotEmpty() }?.map { tool ->
                        DeepSeekTool(
                            type = "function",
                            function = DeepSeekFunctionDefinition(
                                name = tool.name,
                                description = tool.description,
                                parameters = tool.parameters
                            )
                        )
                    },
                    toolChoice = if (request.tools.isNotEmpty()) "auto" else null
                )
            )
        } catch (error: SerializationException) {
            throw AiProviderException.InvalidResponse(error)
        }

        val response = try {
            transport.post(
                url = "https://api.deepseek.com/chat/completions",
                headers = mapOf(
                    "Authorization" to "Bearer $key",
                    "Content-Type" to "application/json"
                ),
                body = payload
            )
        } catch (error: AiProviderException) {
            throw error
        } catch (error: IOException) {
            throw mapAiTransportIOException(error)
        }

        when (response.statusCode) {
            200 -> Unit
            401, 403 -> throw AiProviderException.InvalidApiKey()
            408 -> throw AiProviderException.Timeout()
            429 -> throw AiProviderException.RateLimited()
            in 500..599 -> throw AiProviderException.Unavailable()
            in 400..499 -> throw AiProviderException.ProviderRejected(response.statusCode)
            else -> throw AiProviderException.InvalidResponse()
        }

        if (response.body.length > MAX_RESPONSE_BODY_CHARS) {
            throw AiProviderException.InvalidResponse()
        }
        val decoded = try {
            json.decodeFromString(DeepSeekResponse.serializer(), response.body)
        } catch (error: SerializationException) {
            throw AiProviderException.InvalidResponse(error)
        }
        val message = decoded.choices.firstOrNull()?.message
            ?: throw AiProviderException.InvalidResponse()
        if (message.toolCalls.size > MAX_TOOL_CALLS) throw AiProviderException.InvalidResponse()

        val calls = message.toolCalls.map { call ->
            if (call.type != "function") throw AiProviderException.InvalidResponse()
            val validId = call.id.takeIf { it.matches(TOOL_ID) }
                ?: throw AiProviderException.InvalidResponse()
            val validName = call.function.name.takeIf { it.matches(TOOL_NAME) }
                ?: throw AiProviderException.InvalidResponse()
            val arguments = call.function.arguments
                .takeIf { it.length in 2..MAX_ARGUMENT_CHARACTERS }
                ?: throw AiProviderException.InvalidResponse()
            try {
                json.parseToJsonElement(arguments).jsonObject
            } catch (error: Exception) {
                throw AiProviderException.InvalidResponse(error)
            }
            try {
                AiToolCall(validId, validName, arguments)
            } catch (error: IllegalArgumentException) {
                throw AiProviderException.InvalidResponse(error)
            }
        }
        val content = message.content?.trim().orEmpty()
        if (content.length > MAX_RESPONSE_CHARS || (content.isEmpty() && calls.isEmpty())) {
            throw AiProviderException.InvalidResponse()
        }
        return AiCompletion(
            content = content,
            model = decoded.model?.takeIf { it.isNotBlank() } ?: model,
            toolCalls = calls
        )
    }

    private companion object {
        const val MAX_RESPONSE_CHARS = 64_000
        const val MAX_RESPONSE_BODY_CHARS = 64 * 1024
        const val MAX_ARGUMENT_CHARACTERS = 2_000
        const val MAX_TOOL_CALLS = 4
        val TOOL_ID = Regex("^[a-zA-Z0-9_-]{1,128}$")
        val TOOL_NAME = Regex("^[a-zA-Z0-9_-]{1,64}$")
    }
}
