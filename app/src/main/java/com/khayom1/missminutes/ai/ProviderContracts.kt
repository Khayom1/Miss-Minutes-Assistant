package com.khayom1.missminutes.ai

enum class ChatRole(val wireValue: String) {
    USER("user"),
    ASSISTANT("assistant")
}

data class ChatMessage(
    val role: ChatRole,
    val content: String
)

data class ChatRequest(
    val messages: List<ChatMessage>
)

data class ChatResponse(
    val content: String,
    val model: String
)

enum class ProviderErrorCode {
    NOT_CONFIGURED,
    AUTHENTICATION_REQUIRED,
    INVALID_REQUEST,
    RATE_LIMITED,
    NETWORK,
    TIMEOUT,
    SERVICE_UNAVAILABLE,
    INVALID_RESPONSE,
    LOCAL_MODEL_UNAVAILABLE
}

data class ProviderError(
    val code: ProviderErrorCode
)

sealed interface ChatProviderResult {
    data class Success(val response: ChatResponse) : ChatProviderResult
    data class Failure(val error: ProviderError) : ChatProviderResult
}

interface ChatProvider {
    suspend fun complete(request: ChatRequest): ChatProviderResult
}

interface AccessTokenProvider {
    suspend fun accessToken(): String?
}

enum class ProviderMode {
    REMOTE,
    LOCAL
}

class ProviderRouter(
    private val remoteProvider: ChatProvider,
    private val localProvider: ChatProvider?
) {
    suspend fun complete(request: ChatRequest, mode: ProviderMode): ChatProviderResult =
        when (mode) {
            ProviderMode.REMOTE -> remoteProvider.complete(request)
            ProviderMode.LOCAL -> localProvider?.complete(request)
                ?: ChatProviderResult.Failure(
                    ProviderError(ProviderErrorCode.LOCAL_MODEL_UNAVAILABLE)
                )
        }
}

internal fun ChatRequest.isValid(): Boolean {
    if (messages.isEmpty() || messages.size > 20) return false
    if (messages.none { it.role == ChatRole.USER }) return false

    var totalLength = 0
    return messages.all { message ->
        val valid = message.content.isNotBlank() && message.content.length <= 4_000
        totalLength += message.content.length
        valid && totalLength <= 12_000
    }
}
