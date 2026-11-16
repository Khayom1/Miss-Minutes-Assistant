package com.khayom1.missminutes

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URI
import java.net.URL

enum class ProviderProtocol { OPENAI_COMPATIBLE }

data class ProviderConfig(
    val id: String,
    val displayName: String,
    val baseUrl: String,
    val modelId: String,
    val protocol: ProviderProtocol = ProviderProtocol.OPENAI_COMPATIBLE
) {
    fun validate(apiKey: String) {
        require(id.matches(Regex("[A-Za-z0-9._-]{1,64}"))) { "Provider profile ID is invalid" }
        require(displayName.isNotBlank()) { "Provider name is required" }
        require(modelId.isNotBlank() && modelId.length <= 200) { "Model ID is required" }
        require(apiKey.isNotBlank()) { "Provider API key is required" }
        resolveOpenAiChatEndpoint(baseUrl)
    }
}

data class ProviderCapabilities(
    val supportsSystemMessages: Boolean,
    val supportsConversationHistory: Boolean,
    val supportsModelSelection: Boolean,
    val supportsStreaming: Boolean
)

data class AiMessage(val role: String, val content: String) {
    init {
        require(role in ALLOWED_ROLES) { "Unsupported AI message role" }
        require(content.isNotBlank()) { "AI message content cannot be blank" }
    }

    private companion object {
        val ALLOWED_ROLES = setOf("system", "user", "assistant")
    }
}

interface AiProvider {
    val protocol: ProviderProtocol
    val capabilities: ProviderCapabilities
    suspend fun complete(config: ProviderConfig, apiKey: String, messages: List<AiMessage>): String
}

object ProviderRegistry {
    private val openAiCompatible = OpenAiCompatibleAdapter()

    fun resolve(protocol: ProviderProtocol): AiProvider = when (protocol) {
        ProviderProtocol.OPENAI_COMPATIBLE -> openAiCompatible
    }
}

object AiConversationService {
    suspend fun reply(config: ProviderConfig, apiKey: String, messages: List<AiMessage>): String {
        config.validate(apiKey)
        require(messages.isNotEmpty()) { "At least one message is required" }
        require(messages.size <= MAX_CONTEXT_MESSAGES) { "Conversation context is too large" }
        require(messages.all { it.content.length <= MAX_MESSAGE_CHARS }) { "A message exceeds the supported length" }
        return ProviderRegistry.resolve(config.protocol).complete(config, apiKey, messages)
    }

    private const val MAX_CONTEXT_MESSAGES = 80
    private const val MAX_MESSAGE_CHARS = 12_000
}

class OpenAiCompatibleAdapter : AiProvider {
    override val protocol = ProviderProtocol.OPENAI_COMPATIBLE
    override val capabilities = ProviderCapabilities(
        supportsSystemMessages = true,
        supportsConversationHistory = true,
        supportsModelSelection = true,
        supportsStreaming = false
    )

    override suspend fun complete(
        config: ProviderConfig,
        apiKey: String,
        messages: List<AiMessage>
    ): String = withContext(Dispatchers.IO) {
        config.validate(apiKey)
        val connection = URL(resolveOpenAiChatEndpoint(config.baseUrl)).openConnection() as HttpURLConnection
        connection.instanceFollowRedirects = false
        connection.requestMethod = "POST"
        connection.connectTimeout = CONNECT_TIMEOUT_MS
        connection.readTimeout = READ_TIMEOUT_MS
        connection.doOutput = true
        connection.setRequestProperty("Authorization", "Bearer $apiKey")
        connection.setRequestProperty("Content-Type", "application/json; charset=utf-8")
        connection.setRequestProperty("Accept", "application/json")
        try {
            val body = buildOpenAiChatCompletionRequest(config.modelId, messages)
            connection.outputStream.use { it.write(body.toString().toByteArray(Charsets.UTF_8)) }
            val status = connection.responseCode
            val stream = if (status in 200..299) connection.inputStream else connection.errorStream
            val payload = stream?.use { it.readBoundedText(MAX_RESPONSE_BYTES) }.orEmpty()
            if (status !in 200..299) {
                val detail = runCatching {
                    val error = JSONObject(payload)
                    error.optJSONObject("error")?.optString("message").orEmpty().ifBlank { error.optString("message") }
                }.getOrNull().orEmpty().take(500)
                throw IllegalStateException("${config.displayName} HTTP $status" + if (detail.isBlank()) "" else ": $detail")
            }
            parseOpenAiChatCompletion(payload)
        } finally {
            connection.disconnect()
        }
    }

    private companion object {
        const val CONNECT_TIMEOUT_MS = 15_000
        const val READ_TIMEOUT_MS = 45_000
        const val MAX_RESPONSE_BYTES = 1_048_576
    }
}

internal fun resolveOpenAiChatEndpoint(baseUrl: String): String {
    val trimmed = baseUrl.trim()
    require(trimmed.isNotEmpty()) { "Provider base URL is required" }
    val parsed = try { URI(trimmed) } catch (_: Exception) {
        throw IllegalArgumentException("Provider base URL is invalid")
    }
    require(parsed.scheme.equals("https", ignoreCase = true)) { "Provider base URL must use HTTPS" }
    require(!parsed.host.isNullOrBlank()) { "Provider base URL must include a valid host" }
    require(parsed.rawUserInfo == null && parsed.rawQuery == null && parsed.rawFragment == null) {
        "Provider base URL must not contain credentials, query parameters or fragments"
    }
    val path = parsed.path.orEmpty().trimEnd('/')
    val endpointPath = if (path.endsWith("/chat/completions")) path else "$path/chat/completions"
    return URI("https", null, parsed.host, parsed.port, endpointPath, null, null).toASCIIString()
}

internal fun buildOpenAiChatCompletionRequest(modelId: String, messages: List<AiMessage>): JSONObject {
    require(modelId.isNotBlank()) { "Model ID is required" }
    require(messages.isNotEmpty()) { "At least one message is required" }
    val items = JSONArray()
    messages.forEach { message ->
        items.put(JSONObject().put("role", message.role).put("content", message.content))
    }
    return JSONObject()
        .put("model", modelId)
        .put("stream", false)
        .put("max_tokens", 700)
        .put("temperature", 0.8)
        .put("messages", items)
}

internal fun parseOpenAiChatCompletion(payload: String): String {
    val root = try { JSONObject(payload) } catch (_: Exception) {
        throw IllegalStateException("Provider returned an invalid JSON response")
    }
    val choices = root.optJSONArray("choices") ?: throw IllegalStateException("Provider response is missing choices")
    val choice = choices.optJSONObject(0) ?: throw IllegalStateException("Provider returned no completion choice")
    val message = choice.optJSONObject("message") ?: throw IllegalStateException("Provider response is missing its assistant message")
    val content = (message.opt("content") as? String).orEmpty().trim()
    if (content.isBlank()) throw IllegalStateException("Provider returned an empty assistant response")
    return content
}