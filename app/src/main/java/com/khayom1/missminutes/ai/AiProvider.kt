package com.khayom1.missminutes.ai

import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Headers
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

enum class AiRole(val wireValue: String) {
    SYSTEM("system"),
    USER("user"),
    ASSISTANT("assistant")
}

data class AiMessage(
    val role: AiRole,
    val content: String
)

data class AiRequest(
    val messages: List<AiMessage>,
    val maxOutputTokens: Int = 768
) {
    init {
        require(messages.isNotEmpty()) { "At least one message is required." }
        require(messages.size <= 20) { "A maximum of 20 messages is supported." }
        require(messages.last().role == AiRole.USER) { "The final message must be from the user." }
        require(maxOutputTokens in 1..768) { "maxOutputTokens must be between 1 and 768." }
        require(messages.all { it.content.isNotBlank() && it.content.length <= 8_000 }) {
            "Each message must contain 1–8,000 characters."
        }
        require(messages.sumOf { it.content.length } <= 16_000) {
            "The combined conversation must not exceed 16,000 characters."
        }
    }
}

data class AiCompletion(
    val content: String,
    val model: String
)

sealed class AiProviderException(message: String, cause: Throwable? = null) :
    Exception(message, cause) {
    class LocalWorkspaceUnavailable : AiProviderException("The local workspace is not ready.")
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

/**
 * Adapter seam for an optional on-device runtime (for example, a future
 * llama.cpp/JNI implementation). This class does not download a model or
 * assume that a model is installed.
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
        if (!runtime.isAvailable()) {
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
        .followRedirects(false)
        .followSslRedirects(false)
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
                // Bound bytes while reading. ResponseBody.string() buffers the
                // entire upstream payload before a post-read size check, which
                // could exhaust memory if the gateway or an intermediary misbehaves.
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
