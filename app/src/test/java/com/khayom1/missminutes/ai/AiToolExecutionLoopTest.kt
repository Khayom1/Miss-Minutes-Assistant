package com.khayom1.missminutes.ai

import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AiToolExecutionLoopTest {
    private val schema = Json.parseToJsonElement(
        """{"type":"object","properties":{},"required":[],"additionalProperties":false}"""
    ).jsonObject
    private val tool = AiToolDefinition("plugin_status", "Read device status", schema)

    private class ScriptedProvider(vararg scripted: AiCompletion) : AiProvider {
        val requests = mutableListOf<AiRequest>()
        private val responses = scripted.toMutableList()

        override suspend fun complete(request: AiRequest): AiCompletion {
            requests += request
            return responses.removeAt(0)
        }
    }

    @Test
    fun executesOnlyAdvertisedToolAndReturnsResultToModel() = runBlocking {
        val provider = ScriptedProvider(
            AiCompletion("", "deepseek-chat", listOf(AiToolCall("call_1", "plugin_status", "{}"))),
            AiCompletion("Battery is 78 percent.", "deepseek-chat")
        )
        var executions = 0
        val result = AiToolExecutionLoop(
            provider,
            AiToolExecutor {
                executions++
                AiToolExecutionResult("SUCCESS: 78 percent battery")
            }
        ).complete(
            AiRequest(
                messages = listOf(AiMessage(AiRole.USER, "Check my battery")),
                tools = listOf(tool)
            )
        )

        assertEquals("Battery is 78 percent.", result.content)
        assertEquals(1, executions)
        assertEquals(AiRole.ASSISTANT, provider.requests[1].messages[1].role)
        assertEquals(AiRole.TOOL, provider.requests[1].messages[2].role)
        assertEquals("call_1", provider.requests[1].messages[2].toolCallId)
    }

    @Test
    fun unknownToolNameIsRejectedBeforeAnyExecution() = runBlocking {
        val provider = ScriptedProvider(
            AiCompletion("", "deepseek-chat", listOf(AiToolCall("call_1", "unadvertised_tool", "{}")))
        )
        var executions = 0
        val error = runCatching {
            AiToolExecutionLoop(provider, AiToolExecutor {
                executions++
                AiToolExecutionResult("should not execute")
            }).complete(
                AiRequest(
                    messages = listOf(AiMessage(AiRole.USER, "Do something")),
                    tools = listOf(tool)
                )
            )
        }.exceptionOrNull()

        assertTrue(error is AiProviderException.InvalidResponse)
        assertEquals(0, executions)
    }

    @Test
    fun reusedToolCallIdsAcrossRoundsAreRejectedBeforeSecondExecution() = runBlocking {
        val provider = ScriptedProvider(
            AiCompletion("", "deepseek-chat", listOf(AiToolCall("call_1", "plugin_status", "{}"))),
            AiCompletion("", "deepseek-chat", listOf(AiToolCall("call_1", "plugin_status", "{}")))
        )
        var executions = 0
        val error = runCatching {
            AiToolExecutionLoop(provider, AiToolExecutor {
                executions++
                AiToolExecutionResult("ok")
            }).complete(
                AiRequest(
                    messages = listOf(AiMessage(AiRole.USER, "Check battery")),
                    tools = listOf(tool)
                )
            )
        }.exceptionOrNull()

        assertTrue(error is AiProviderException.InvalidResponse)
        assertEquals(1, executions)
    }

    @Test
    fun toolResultsAreTruncatedToRemainingConversationBudget() = runBlocking {
        val provider = ScriptedProvider(
            AiCompletion("", "deepseek-chat", listOf(AiToolCall("call_1", "plugin_status", "{}"))),
            AiCompletion("done", "deepseek-chat")
        )
        val initialMessages = listOf(
            AiMessage(AiRole.SYSTEM, "a".repeat(8_000)),
            AiMessage(AiRole.SYSTEM, "b".repeat(8_000)),
            AiMessage(AiRole.USER, "c".repeat(3_500))
        )
        AiToolExecutionLoop(provider, AiToolExecutor {
            AiToolExecutionResult("x".repeat(1_000))
        }).complete(AiRequest(messages = initialMessages, tools = listOf(tool)))

        val followUp = provider.requests[1].messages
        assertEquals(20_000, followUp.sumOf { it.content.length })
        assertEquals(500, followUp.last().content.length)
        assertEquals(AiRole.TOOL, followUp.last().role)
    }

    @Test
    fun toolLoopRejectsMessageCountOverflowBeforeExecuting() = runBlocking {
        val provider = ScriptedProvider(
            AiCompletion("", "deepseek-chat", listOf(AiToolCall("call_1", "plugin_status", "{}")))
        )
        val messages = (1..31).map { AiMessage(AiRole.SYSTEM, "system") } +
            AiMessage(AiRole.USER, "final user message")
        var executions = 0
        val error = runCatching {
            AiToolExecutionLoop(provider, AiToolExecutor {
                executions++
                AiToolExecutionResult("should not execute")
            }).complete(AiRequest(messages = messages, tools = listOf(tool)))
        }.exceptionOrNull()

        assertTrue(error is AiProviderException.InvalidResponse)
        assertEquals(0, executions)
    }

    @Test
    fun toolRoundLimitStopsUnboundedModelLoops() = runBlocking {
        val provider = ScriptedProvider(
            AiCompletion("", "deepseek-chat", listOf(AiToolCall("call_1", "plugin_status", "{}"))),
            AiCompletion("", "deepseek-chat", listOf(AiToolCall("call_2", "plugin_status", "{}")))
        )
        val error = runCatching {
            AiToolExecutionLoop(
                provider,
                AiToolExecutor { AiToolExecutionResult("ok") },
                maxToolRounds = 1
            ).complete(
                AiRequest(
                    messages = listOf(AiMessage(AiRole.USER, "Check battery")),
                    tools = listOf(tool)
                )
            )
        }.exceptionOrNull()

        assertTrue(error is AiProviderException.InvalidResponse)
    }

    @Test
    fun duplicateSideEffectCallsWithDifferentIdsAreRejectedBeforeExecution() = runBlocking {
        val provider = ScriptedProvider(
            AiCompletion(
                "",
                "deepseek-chat",
                listOf(
                    AiToolCall("call_1", "plugin_status", "{}"),
                    AiToolCall("call_2", "plugin_status", "{}")
                )
            )
        )
        var executions = 0
        val error = runCatching {
            AiToolExecutionLoop(provider, AiToolExecutor {
                executions++
                AiToolExecutionResult("ok")
            }).complete(
                AiRequest(
                    messages = listOf(AiMessage(AiRole.USER, "Do the action once")),
                    tools = listOf(tool)
                )
            )
        }.exceptionOrNull()

        assertTrue(error is AiProviderException.InvalidResponse)
        assertEquals(0, executions)
    }

    @Test
    fun duplicateSideEffectCallsAcrossRoundsAreRejectedEvenWithNewCallIds() = runBlocking {
        val provider = ScriptedProvider(
            AiCompletion("", "deepseek-chat", listOf(AiToolCall("call_1", "plugin_status", "{}"))),
            AiCompletion("", "deepseek-chat", listOf(AiToolCall("call_2", "plugin_status", "{}")))
        )
        var executions = 0
        val error = runCatching {
            AiToolExecutionLoop(provider, AiToolExecutor {
                executions++
                AiToolExecutionResult("ok")
            }).complete(
                AiRequest(
                    messages = listOf(AiMessage(AiRole.USER, "Do the action once")),
                    tools = listOf(tool)
                )
            )
        }.exceptionOrNull()

        assertTrue(error is AiProviderException.InvalidResponse)
        assertEquals(1, executions)
    }
    @Test
    fun duplicateActionArgumentsAreCanonicalizedIndependentOfObjectKeyOrder() = runBlocking {
        val provider = ScriptedProvider(
            AiCompletion(
                "",
                "deepseek-chat",
                listOf(
                    AiToolCall("call_1", "plugin_status", """{"x":1,"y":2}"""),
                    AiToolCall("call_2", "plugin_status", """{"y":2,"x":1}""")
                )
            )
        )
        var executions = 0
        val error = runCatching {
            AiToolExecutionLoop(provider, AiToolExecutor {
                executions++
                AiToolExecutionResult("ok")
            }).complete(
                AiRequest(
                    messages = listOf(AiMessage(AiRole.USER, "Do the action once")),
                    tools = listOf(tool)
                )
            )
        }.exceptionOrNull()

        assertTrue(error is AiProviderException.InvalidResponse)
        assertEquals(0, executions)
    }

}
