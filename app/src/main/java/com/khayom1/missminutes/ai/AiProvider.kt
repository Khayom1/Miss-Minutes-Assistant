package com.khayom1.missminutes.ai

import java.io.IOException
import java.io.InterruptedIOException
import java.net.SocketTimeoutException
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.serialization.json.JsonObject
import okhttp3.Call
import okhttp3.Callback
import okhttp3.Headers
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response

enum class AiRole(val wireValue: String) {
    SYSTEM("system"),
    USER("user"),
    ASSISTANT("assistant"),
    TOOL("tool")
}

data class AiToolDefinition(
    val name: String,
    val description: String,
    val parameters: JsonObject,
    /** True only for actions whose execution can change external state. */
    val sideEffect: Boolean = false
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
    class MissingApiKey : AiProviderException("A DeepSeek API key must be added in Settings.")
    class InvalidApiKey : AiProviderException("The DeepSeek API key was rejected.")
    class RateLimited : AiProviderException("The AI service rate limit was reached.")
    class Timeout(cause: Throwable? = null) :
        AiProviderException("The AI service request timed out.", cause)
    class ProviderRejected(val statusCode: Int) :
        AiProviderException("The AI service rejected the request (HTTP $statusCode).") {
        init {
            require(statusCode in 400..499)
        }
    }
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
 * No on-device model runtime is bundled or initialized by this application.
 * Keep this unavailable until a real, lifecycle-managed inference engine ships.
 */
object LocalInferenceStatus {
    const val isAvailable: Boolean = false
    const val reason: String = "No on-device inference engine is installed."
}

/**
 * Production OkHttp transport with bounded timeouts. Requests use OkHttp's
 * asynchronous dispatcher and cancel the underlying call with the coroutine.
 */
class OkHttpAiHttpTransport(
    internal val client: OkHttpClient = OkHttpClient.Builder()
        .retryOnConnectionFailure(false)
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
    ): AiHttpResponse = suspendCancellableCoroutine { continuation ->
        val requestHeaders = Headers.Builder().apply {
            headers.forEach { (name, value) -> add(name, value) }
        }.build()

        val request = Request.Builder()
            .url(url)
            .headers(requestHeaders)
            .post(body.toRequestBody("application/json; charset=utf-8".toMediaType()))
            .build()

        val call = client.newCall(request)
        continuation.invokeOnCancellation { call.cancel() }
        call.enqueue(object : Callback {
            override fun onFailure(call: Call, error: IOException) {
                if (continuation.isActive) {
                    continuation.resumeWith(Result.failure(mapAiTransportIOException(error)))
                }
            }

            override fun onResponse(call: Call, response: Response) {
                val outcome: Result<AiHttpResponse> = try {
                    response.use {
                        val source = it.body?.source()
                        val text = if (source == null) {
                            ""
                        } else {
                            if (source.request(MAX_RESPONSE_BYTES + 1L)) {
                                throw AiProviderException.InvalidResponse()
                            }
                            source.readUtf8()
                        }
                        Result.success(AiHttpResponse(statusCode = it.code, body = text))
                    }
                } catch (error: AiProviderException) {
                    Result.failure(error)
                } catch (error: IOException) {
                    Result.failure(mapAiTransportIOException(error))
                } catch (_: Exception) {
                    Result.failure(AiProviderException.Unavailable())
                }

                if (continuation.isActive) continuation.resumeWith(outcome)
            }
        })
    }

    private companion object {
        const val MAX_RESPONSE_BYTES = 64 * 1024
    }
}

internal fun mapAiTransportIOException(error: IOException): AiProviderException {
    val timedOut = error is SocketTimeoutException ||
        (error is InterruptedIOException &&
            error.message?.contains("timeout", ignoreCase = true) == true)
    return if (timedOut) AiProviderException.Timeout(error) else AiProviderException.Unavailable(error)
}
