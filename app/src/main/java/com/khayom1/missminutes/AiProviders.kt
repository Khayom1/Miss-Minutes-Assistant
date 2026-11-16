package com.khayom1.missminutes

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URI
import java.net.URL
import java.net.URLEncoder

enum class ProviderProtocol { OPENAI_COMPATIBLE, GEMINI, ANTHROPIC }

data class ProviderConfig(
    val id: String,
    val displayName: String,
    val baseUrl: String,
    val modelId: String,
    val protocol: ProviderProtocol = inferProviderProtocol(baseUrl)
) {
    fun validate(apiKey: String) {
        require(id.matches(Regex("[A-Za-z0-9._-]{1,64}"))) { "Provider profile ID is invalid" }
        require(displayName.isNotBlank()) { "Provider name is required" }
        require(modelId.matches(Regex("[A-Za-z0-9._:-]{1,200}"))) { "Model ID is invalid" }
        require(apiKey.isNotBlank()) { "Provider API key is required" }
        resolveProviderEndpoint(this)
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
    private companion object { val ALLOWED_ROLES = setOf("system", "user", "assistant") }
}

interface AiProvider {
    val protocol: ProviderProtocol
    val capabilities: ProviderCapabilities
    suspend fun complete(config: ProviderConfig, apiKey: String, messages: List<AiMessage>): String
}

object ProviderRegistry {
    private val openAiCompatible = OpenAiCompatibleAdapter()
    private val gemini = GeminiAdapter()
    private val anthropic = AnthropicAdapter()
    fun resolve(protocol: ProviderProtocol): AiProvider = when (protocol) {
        ProviderProtocol.OPENAI_COMPATIBLE -> openAiCompatible
        ProviderProtocol.GEMINI -> gemini
        ProviderProtocol.ANTHROPIC -> anthropic
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

abstract class HttpAiAdapter : AiProvider {
    override val capabilities = ProviderCapabilities(true, true, true, false)
    protected suspend fun post(
        endpoint: String,
        headers: Map<String, String>,
        body: JSONObject,
        parse: (String) -> String,
        providerName: String
    ): String = withContext(Dispatchers.IO) {
        val connection = URL(endpoint).openConnection() as HttpURLConnection
        connection.instanceFollowRedirects = false
        connection.requestMethod = "POST"
        connection.connectTimeout = CONNECT_TIMEOUT_MS
        connection.readTimeout = READ_TIMEOUT_MS
        connection.doOutput = true
        connection.setRequestProperty("Content-Type", "application/json; charset=utf-8")
        connection.setRequestProperty("Accept", "application/json")
        headers.forEach { (name, value) -> connection.setRequestProperty(name, value) }
        try {
            connection.outputStream.use { it.write(body.toString().toByteArray(Charsets.UTF_8)) }
            val status = connection.responseCode
            val stream = if (status in 200..299) connection.inputStream else connection.errorStream
            val payload = stream?.use { it.readBoundedText(MAX_RESPONSE_BYTES) }.orEmpty()
            if (status !in 200..299) {
                val detail = runCatching {
                    val error = JSONObject(payload)
                    error.optJSONObject("error")?.optString("message").orEmpty()
                        .ifBlank { error.optString("message") }
                }.getOrNull().orEmpty().take(500)
                throw IllegalStateException("$providerName HTTP $status" + if (detail.isBlank()) "" else ": $detail")
            }
            parse(payload)
        } finally {
            connection.disconnect()
        }
    }
    protected companion object {
        const val CONNECT_TIMEOUT_MS = 15_000
        const val READ_TIMEOUT_MS = 45_000
        const val MAX_RESPONSE_BYTES = 1_048_576
    }
}

class OpenAiCompatibleAdapter : HttpAiAdapter() {
    override val protocol = ProviderProtocol.OPENAI_COMPATIBLE
    override suspend fun complete(config: ProviderConfig, apiKey: String, messages: List<AiMessage>): String {
        config.validate(apiKey)
        return post(
            resolveOpenAiChatEndpoint(config.baseUrl),
            mapOf("Authorization" to "Bearer $apiKey"),
            buildOpenAiChatCompletionRequest(config.modelId, messages),
            ::parseOpenAiChatCompletion,
            config.displayName
        )
    }
}

class GeminiAdapter : HttpAiAdapter() {
    override val protocol = ProviderProtocol.GEMINI
    override suspend fun complete(config: ProviderConfig, apiKey: String, messages: List<AiMessage>): String {
        config.validate(apiKey)
        return post(
            resolveGeminiEndpoint(config.baseUrl, config.modelId),
            mapOf("x-goog-api-key" to apiKey),
            buildGeminiGenerateContentRequest(messages),
            ::parseGeminiGenerateContent,
            config.displayName
        )
    }
}

class AnthropicAdapter : HttpAiAdapter() {
    override val protocol = ProviderProtocol.ANTHROPIC
    override suspend fun complete(config: ProviderConfig, apiKey: String, messages: List<AiMessage>): String {
        config.validate(apiKey)
        return post(
            resolveAnthropicMessagesEndpoint(config.baseUrl),
            mapOf("x-api-key" to apiKey, "anthropic-version" to "2023-06-01"),
            buildAnthropicMessagesRequest(config.modelId, messages),
            ::parseAnthropicMessages,
            config.displayName
        )
    }
}

fun inferProviderProtocol(baseUrl: String): ProviderProtocol {
    val host = runCatching { URI(baseUrl.trim()).host?.lowercase() }.getOrNull()
    return when (host) {
        "generativelanguage.googleapis.com" -> ProviderProtocol.GEMINI
        "api.anthropic.com" -> ProviderProtocol.ANTHROPIC
        else -> ProviderProtocol.OPENAI_COMPATIBLE
    }
}

private fun validatedHttpsBase(baseUrl: String, expectedHost: String? = null): URI {
    val parsed = try { URI(baseUrl.trim()) } catch (_: Exception) {
        throw IllegalArgumentException("Provider base URL is invalid")
    }
    require(parsed.scheme.equals("https", ignoreCase = true)) { "Provider base URL must use HTTPS" }
    require(!parsed.host.isNullOrBlank()) { "Provider base URL must include a valid host" }
    require(parsed.rawUserInfo == null && parsed.rawQuery == null && parsed.rawFragment == null) {
        "Provider base URL must not contain credentials, query parameters or fragments"
    }
    require(parsed.port == -1 || parsed.port == 443) { "Provider base URL must use the standard HTTPS port" }
    if (expectedHost != null) require(parsed.host.equals(expectedHost, true)) { "Provider host is not valid for this protocol" }
    return parsed
}

internal fun resolveProviderEndpoint(config: ProviderConfig): String = when (config.protocol) {
    ProviderProtocol.OPENAI_COMPATIBLE -> resolveOpenAiChatEndpoint(config.baseUrl)
    ProviderProtocol.GEMINI -> resolveGeminiEndpoint(config.baseUrl, config.modelId)
    ProviderProtocol.ANTHROPIC -> resolveAnthropicMessagesEndpoint(config.baseUrl)
}

internal fun resolveOpenAiChatEndpoint(baseUrl: String): String {
    val parsed = validatedHttpsBase(baseUrl)
    val path = parsed.path.orEmpty().trimEnd('/')
    val endpointPath = if (path.endsWith("/chat/completions")) path else "$path/chat/completions"
    return URI("https", null, parsed.host, -1, endpointPath, null, null).toASCIIString()
}

internal fun resolveGeminiEndpoint(baseUrl: String, modelId: String): String {
    require(modelId.isNotBlank()) { "Gemini model ID is required" }
    val parsed = validatedHttpsBase(baseUrl, "generativelanguage.googleapis.com")
    val path = parsed.path.orEmpty().trimEnd('/')
    require(path.isEmpty() || path == "/v1beta" || path == "/v1") { "Gemini base URL path must be empty, /v1 or /v1beta" }
    val version = if (path == "/v1") "v1" else "v1beta"
    val model = URLEncoder.encode(modelId, "UTF-8").replace("+", "%20")
    return "https://generativelanguage.googleapis.com/$version/models/$model:generateContent"
}

internal fun resolveAnthropicMessagesEndpoint(baseUrl: String): String {
    val parsed = validatedHttpsBase(baseUrl, "api.anthropic.com")
    val path = parsed.path.orEmpty().trimEnd('/')
    require(path.isEmpty() || path == "/v1" || path == "/v1/messages") {
        "Anthropic base URL path must be empty, /v1 or /v1/messages"
    }
    return "https://api.anthropic.com/v1/messages"
}

internal fun buildOpenAiChatCompletionRequest(modelId: String, messages: List<AiMessage>): JSONObject {
    require(modelId.isNotBlank()) { "Model ID is required" }
    require(messages.isNotEmpty()) { "At least one message is required" }
    val items = JSONArray()
    messages.forEach { items.put(JSONObject().put("role", it.role).put("content", it.content)) }
    return JSONObject().put("model", modelId).put("stream", false)
        .put("max_tokens", 700).put("temperature", 0.8).put("messages", items)
}

internal fun buildGeminiGenerateContentRequest(messages: List<AiMessage>): JSONObject {
    require(messages.isNotEmpty()) { "At least one message is required" }
    val system = messages.filter { it.role == "system" }.joinToString("\n\n") { it.content }
    val contents = JSONArray()
    messages.filter { it.role != "system" }.forEach { message ->
        contents.put(JSONObject()
            .put("role", if (message.role == "assistant") "model" else "user")
            .put("parts", JSONArray().put(JSONObject().put("text", message.content))))
    }
    require(contents.length() > 0) { "Gemini requires at least one user or assistant message" }
    val body = JSONObject()
        .put("contents", contents)
        .put("generationConfig", JSONObject().put("temperature", 0.8).put("maxOutputTokens", 700))
    if (system.isNotBlank()) body.put("systemInstruction", JSONObject().put("parts", JSONArray().put(JSONObject().put("text", system))))
    return body
}

internal fun buildAnthropicMessagesRequest(modelId: String, messages: List<AiMessage>): JSONObject {
    require(modelId.isNotBlank()) { "Anthropic model ID is required" }
    require(messages.isNotEmpty()) { "At least one message is required" }
    val system = messages.filter { it.role == "system" }.joinToString("\n\n") { it.content }
    val content = JSONArray()
    messages.filter { it.role != "system" }.forEach { message ->
        content.put(JSONObject().put("role", if (message.role == "assistant") "assistant" else "user")
            .put("content", JSONArray().put(JSONObject().put("type", "text").put("text", message.content))))
    }
    require(content.length() > 0) { "Anthropic requires at least one user or assistant message" }
    val body = JSONObject().put("model", modelId).put("max_tokens", 700).put("messages", content)
    if (system.isNotBlank()) body.put("system", system)
    return body
}

internal fun parseOpenAiChatCompletion(payload: String): String {
    val root = parseJsonObject(payload, "Provider")
    val choices = root.optJSONArray("choices") ?: throw IllegalStateException("Provider response is missing choices")
    val choice = choices.optJSONObject(0) ?: throw IllegalStateException("Provider returned no completion choice")
    val message = choice.optJSONObject("message") ?: throw IllegalStateException("Provider response is missing its assistant message")
    return (message.opt("content") as? String).orEmpty().trim().ifBlank {
        throw IllegalStateException("Provider returned an empty assistant response")
    }
}

internal fun parseGeminiGenerateContent(payload: String): String {
    val root = parseJsonObject(payload, "Gemini")
    val candidates = root.optJSONArray("candidates") ?: throw IllegalStateException("Gemini response is missing candidates")
    val candidate = candidates.optJSONObject(0) ?: throw IllegalStateException("Gemini returned no completion candidate")
    val finish = candidate.optString("finishReason")
    if (finish == "MAX_TOKENS") throw IllegalStateException("Gemini response was truncated by the output-token limit")
    if (finish == "SAFETY") throw IllegalStateException("Gemini blocked the response with its safety filters")
    val parts = candidate.optJSONObject("content")?.optJSONArray("parts")
        ?: throw IllegalStateException("Gemini response is missing content parts")
    val text = buildString {
        for (i in 0 until parts.length()) append(parts.optJSONObject(i)?.optString("text").orEmpty())
    }.trim()
    return text.ifBlank { throw IllegalStateException("Gemini returned an empty assistant response") }
}

internal fun parseAnthropicMessages(payload: String): String {
    val root = parseJsonObject(payload, "Anthropic")
    val content = root.optJSONArray("content") ?: throw IllegalStateException("Anthropic response is missing content")
    val text = buildString {
        for (i in 0 until content.length()) {
            val block = content.optJSONObject(i) ?: continue
            if (block.optString("type") == "text") append(block.optString("text"))
        }
    }.trim()
    if (root.optString("stop_reason") == "max_tokens") throw IllegalStateException("Anthropic response was truncated by the output-token limit")
    return text.ifBlank { throw IllegalStateException("Anthropic returned an empty assistant response") }
}

private fun parseJsonObject(payload: String, provider: String): JSONObject =
    try { JSONObject(payload) } catch (_: Exception) {
        throw IllegalStateException("$provider returned an invalid JSON response")
    }
