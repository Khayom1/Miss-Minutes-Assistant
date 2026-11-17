package com.khayom1.missminutes.ai

import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import okhttp3.Headers
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

enum class AiRole(val wireValue: String) {
    SYSTEM("system"),
    USER("user"),
    ASSISTANT("assistant"),
    TOOL("tool")
}

data class AiToolDefinition(
    val name: String,
    val description: String,
    val parameters: JsonObject
) {
    init {
        require(name.matches(Regex("^[a-zA-Z0-9_-]{1,64}$")))
        require(description.isNotBlank() && description.length <= 512)
        require(parameters.toString().length <= 8_192)
    }
}

data class AiToolCall(
    val id: String,
    val name: String,
    val arguments: String
) {
    init {
        require(id.matches(Regex("^[a-zA-Z0-9_-]{1,128}$")))
        require(name.matches(Regex("^[a-zA-Z0-9_-]{1,64}$")))
        require(arguments.length in 2..2_000)
    }
}

data class AiMessage(
    val role: AiRole,
    val content: String,
    val toolCallId: String? = null,
    val name: String? = null,
    val toolCalls: List<AiToolCall> = emptyList()
)

data class AiRequest(
    val messages: List<AiMessage>,
    val maxOutputTokens: Int = 768,
    val tools: List<AiToolDefinition> = emptyList()
) {
    init {
        require(messages.isNotEmpty()) { "At least one message is required." }
        require(messages.size <= 32) { "A maximum of 32 messages is supported." }
        require(messages.last().role == AiRole.USER || messages.last().role == AiRole.TOOL) {
            "The final message must be from the user or a tool."
        }
        require(maxOutputTokens in 1..768) { "maxOutputTokens must be between 1 and 768." }
        require(messages.all {
            (it.content.isNotBlank() || (it.role == AiRole.ASSISTANT && it.toolCalls.isNotEmpty())) &&
                it.content.length <= 8_000
        }) { "Each message must contain at most 8,000 characters and non-empty content unless it carries tool calls." }
        require(messages.sumOf { it.content.length } <= 20_000) {
            "The combined conversation must not exceed 20,000 characters."
        }
        require(messages.all { message ->
            when (message.role) {
                AiRole.TOOL -> !message.toolCallId.isNullOrBlank() &&
                    !message.name.isNullOrBlank() && message.toolCalls.isEmpty()
                AiRole.ASSISTANT -> message.toolCallId == null && message.name == null &&
                    message.toolCalls.size <= 4
                AiRole.SYSTEM, AiRole.USER -> message.toolCallId == null &&
                    message.name == null && message.toolCalls.isEmpty()
            }
        }) { "Tool metadata is not valid for this message role." }
        require(tools.size <= 16) { "A maximum of 16 tools is supported." }
        require(tools.map { it.name }.distinct().size == tools.size) {
            "Tool names must be unique."
        }
    }
}

data class AiCompletion(
    val content: String,
    val model: String,
    val toolCalls: List<AiToolCall> = emptyList()
)

sealed class AiProviderException(message: String, cause: Throwable? = null) :
    Exception(message, cause) {
    class AuthenticationRequired : AiProviderException("A valid signed-in session is required.")
    class MissingApiKey : AiProviderException("A DeepSeek API key must be added in Settings.")
    class InvalidApiKey : AiProviderException("The DeepSeek API key was rejected.")
    class RateLimited : AiProviderException("The AI service rate limit was reached.")
    class Unavailable(cause: Throwable? = null) :
        AiProviderException("The AI service is currently unavailable.", cause)
    class InvalidResponse(cause: Throwable? = null) :
        AiProviderException("The AI service returned an invalid response.", cause)
}

interface AiProvider {
    suspend fun complete(request: AiRequest): AiCompletion
}

fun interface AccessTokenSource {
    suspend fun currentAccessToken(): String?
}

data class AiHttpResponse(
    val statusCode: Int,
    val body: String
)

interface AiHttpTransport {
    suspend fun post(
        url: String,
        headers: Map<String, String>,
        body: String
    ): AiHttpResponse
}

@Serializable
private data class FunctionRequest(
    val messages: List<WireMessage>
)

@Serializable
private data class WireMessage(
    val role: String,
    val content: String
)

@Serializable
private data class FunctionResponse(
    val content: String,
    val model: String = "deepseek-chat"
)

/**
 * Calls only the authenticated Supabase Edge Function. The DeepSeek provider
 * credential is never stored in or transmitted from the Android application.
 */
class SupabaseDeepSeekProvider(
    private val functionUrl: String,
    private val publishableKey: String,
    private val accessTokenSource: AccessTokenSource,
    private val transport: AiHttpTransport,
    private val json: Json = Json { ignoreUnknownKeys = true }
) : AiProvider {
    init {
        require(functionUrl.startsWith("https://")) {
            "The AI function endpoint must use HTTPS."
        }
        require(publishableKey.isNotBlank()) {
            "A Supabase publishable/anon key is required."
        }
    }

    override suspend fun complete(request: AiRequest): AiCompletion {
        if (request.tools.isNotEmpty() || request.messages.any { it.role == AiRole.TOOL || it.toolCalls.isNotEmpty() }) {
            throw AiProviderException.Unavailable()
        }
        val token = accessTokenSource.currentAccessToken()
            ?.takeIf { it.isNotBlank() }
            ?: throw AiProviderException.AuthenticationRequired()

        val requestBody = try {
            json.encodeToString(
                FunctionRequest.serializer(),
                FunctionRequest(
                    messages = request.messages.map {
                        WireMessage(role = it.role.wireValue, content = it.content)
                    }
                )
            )
        } catch (error: SerializationException) {
            throw AiProviderException.InvalidResponse(error)
        }

        val response = try {
            transport.post(
                url = functionUrl,
                headers = mapOf(
                    "Authorization" to "Bearer $token",
                    "apikey" to publishableKey,
                    "Content-Type" to "application/json"
                ),
                body = requestBody
            )
        } catch (error: IOException) {
            throw AiProviderException.Unavailable(error)
        }

        when (response.statusCode) {
            200 -> Unit
            401, 403 -> throw AiProviderException.AuthenticationRequired()
            429 -> throw AiProviderException.RateLimited()
            in 500..599 -> throw AiProviderException.Unavailable()
            else -> throw AiProviderException.InvalidResponse()
        }

        val decoded = try {
            json.decodeFromString(FunctionResponse.serializer(), response.body)
        } catch (error: SerializationException) {
            throw AiProviderException.InvalidResponse(error)
        }

        val text = decoded.content.trim()
        if (text.isEmpty() || text.length > MAX_RESPONSE_CHARS) {
            throw AiProviderException.InvalidResponse()
        }

        return AiCompletion(
            content = text,
            model = decoded.model.ifBlank { "deepseek-chat" }
        )
    }

    private companion object {
        const val MAX_RESPONSE_CHARS = 64_000
    }
}

/**
 * Adapter seam for an optional on-device runtime. It does not download a model
 * or assume that a model is installed. Tool execution is unavailable until the
 * local runtime exposes a structured tool-call contract.
 */
interface LocalInferenceRuntime {
    fun isAvailable(): Boolean

    suspend fun generate(
        messages: List<AiMessage>,
        maxOutputTokens: Int
    ): String
}

class OptionalLocalAiProvider(
    private val runtime: LocalInferenceRuntime
) : AiProvider {
    override suspend fun complete(request: AiRequest): AiCompletion {
        if (!runtime.isAvailable() || request.tools.isNotEmpty() ||
            request.messages.any { it.role == AiRole.TOOL || it.toolCalls.isNotEmpty() }
        ) {
            throw AiProviderException.Unavailable()
        }

        val output = try {
            runtime.generate(request.messages, request.maxOutputTokens).trim()
        } catch (error: IOException) {
            throw AiProviderException.Unavailable(error)
        }

        if (output.isEmpty()) throw AiProviderException.InvalidResponse()
        return AiCompletion(content = output, model = "local")
    }
}

/**
 * Production OkHttp transport with a hard request timeout. Network work runs
 * on Dispatchers.IO and never blocks the Compose main thread.
 */
class OkHttpAiHttpTransport(
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(45, TimeUnit.SECONDS)
        .callTimeout(50, TimeUnit.SECONDS)
        .build()
) : AiHttpTransport {
    override suspend fun post(
        url: String,
        headers: Map<String, String>,
        body: String
    ): AiHttpResponse = withContext(Dispatchers.IO) {
        val requestHeaders = Headers.Builder().apply {
            headers.forEach { (name, value) -> add(name, value) }
        }.build()

        val request = Request.Builder()
            .url(url)
            .headers(requestHeaders)
            .post(body.toRequestBody("application/json; charset=utf-8".toMediaType()))
            .build()

        try {
            client.newCall(request).execute().use { response ->
                val text = response.body?.source()?.use { source ->
                    if (source.request(MAX_RESPONSE_BYTES + 1L)) {
                        throw AiProviderException.InvalidResponse()
                    }
                    source.readUtf8()
                }.orEmpty()
                AiHttpResponse(statusCode = response.code, body = text)
            }
        } catch (error: AiProviderException) {
            throw error
        } catch (error: IOException) {
            throw AiProviderException.Unavailable(error)
        }
    }

    private companion object {
        const val MAX_RESPONSE_BYTES = 64 * 1024
    }
}
