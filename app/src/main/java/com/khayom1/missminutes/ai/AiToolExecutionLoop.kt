package com.khayom1.missminutes.ai

import kotlinx.coroutines.CancellationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/** A bounded provider/tool loop. Tool names must come from the request's connected catalog. */
data class AiToolExecutionResult(val content: String)

fun interface AiToolExecutor {
    suspend fun execute(call: AiToolCall): AiToolExecutionResult
}

class AiToolExecutionLoop(
    private val provider: AiProvider,
    private val executor: AiToolExecutor,
    private val maxToolRounds: Int = 2,
    private val maxToolCalls: Int = 4
) {
    init {
        require(maxToolRounds in 1..4)
        require(maxToolCalls in 1..8)
    }

    suspend fun complete(request: AiRequest): AiCompletion {
        val allowedNames = request.tools.map { it.name }.toSet()
        val messages = request.messages.toMutableList()
        val seenCallIds = mutableSetOf<String>()
        val seenActionFingerprints = mutableSetOf<String>()
        var completion = provider.complete(request)
        var executedCalls = 0
        var rounds = 0

        while (completion.toolCalls.isNotEmpty()) {
            if (request.tools.isEmpty() || rounds >= maxToolRounds) {
                throw AiProviderException.InvalidResponse()
            }
            val calls = completion.toolCalls
            val actionFingerprints = calls.map(::actionFingerprint)
            if (calls.size > 4 ||
                calls.map { it.id }.distinct().size != calls.size ||
                calls.any { it.id in seenCallIds || it.name !in allowedNames } ||
                actionFingerprints.distinct().size != actionFingerprints.size ||
                actionFingerprints.any { it in seenActionFingerprints } ||
                executedCalls + calls.size > maxToolCalls ||
                messages.size + 1 + calls.size > MAX_MESSAGES
            ) {
                throw AiProviderException.InvalidResponse()
            }

            val baseCharacters = messages.sumOf { it.content.length } + completion.content.length
            if (completion.content.length > MAX_MESSAGE_CONTENT_CHARACTERS ||
                baseCharacters > MAX_MESSAGE_CHARACTERS
            ) {
                throw AiProviderException.InvalidResponse()
            }
            var remainingCharacters = MAX_MESSAGE_CHARACTERS - baseCharacters

            seenCallIds += calls.map { it.id }
            seenActionFingerprints += actionFingerprints
            messages += AiMessage(
                role = AiRole.ASSISTANT,
                content = completion.content,
                toolCalls = calls
            )
            for (call in calls) {
                val result = try {
                    executor.execute(call).content.trim()
                } catch (error: CancellationException) {
                    throw error
                } catch (_: Exception) {
                    "TOOL_EXECUTION_FAILED: The action could not be completed safely."
                }
                val candidate = result.take(MAX_TOOL_RESULT_CHARACTERS).ifBlank {
                    "TOOL_EXECUTION_FAILED: The action returned no result."
                }
                val safeResult = candidate.take(remainingCharacters)
                if (safeResult.isBlank()) throw AiProviderException.InvalidResponse()
                remainingCharacters -= safeResult.length
                messages += AiMessage(
                    role = AiRole.TOOL,
                    content = safeResult,
                    toolCallId = call.id,
                    name = call.name
                )
                executedCalls++
            }

            completion = provider.complete(request.copy(messages = messages.toList()))
            rounds++
        }
        return completion
    }

    private fun actionFingerprint(call: AiToolCall): String {
        val canonicalArguments = runCatching {
            canonicalizeArguments(Json.parseToJsonElement(call.arguments))
        }.getOrElse {
            call.arguments.trim()
        }
        return call.name + ":" + canonicalArguments
    }

    private fun canonicalizeArguments(element: JsonElement): String = when (element) {
        is JsonObject -> element.entries
            .sortedBy { it.key }
            .joinToString(prefix = "{", postfix = "}") { (key, value) ->
                JsonPrimitive(key).toString() + ":" + canonicalizeArguments(value)
            }
        is JsonArray -> element.joinToString(prefix = "[", postfix = "]") {
            canonicalizeArguments(it)
        }
        else -> element.toString()
    }

    private companion object {
        const val MAX_MESSAGES = 32
        const val MAX_MESSAGE_CHARACTERS = 20_000
        const val MAX_MESSAGE_CONTENT_CHARACTERS = 8_000
        const val MAX_TOOL_RESULT_CHARACTERS = 1_000
    }
}
