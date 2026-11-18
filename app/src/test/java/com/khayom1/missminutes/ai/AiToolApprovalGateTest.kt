package com.khayom1.missminutes.ai

import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AiToolApprovalGateTest {
    private val prompt = AiToolApprovalPrompt(
        toolName = "calendar_create_event",
        pluginName = "Calendar",
        description = "Create a calendar event.",
        argumentNames = listOf("date", "title")
    )

    @Test
    fun approvalResolvesAndClearsPendingPrompt() = runBlocking {
        val gate = AiToolApprovalGate()
        val result = async(start = CoroutineStart.UNDISPATCHED) {
            gate.awaitApproval(prompt)
        }
        val requestId = requireNotNull(gate.pending.value).requestId

        assertTrue(gate.resolve(requestId, approved = true))
        assertTrue(result.await())
        assertNull(gate.pending.value)
        assertFalse(gate.resolve(requestId, approved = true))
    }

    @Test
    fun rejectionIsReturnedToTheToolExecutor() = runBlocking {
        val gate = AiToolApprovalGate()
        val result = async(start = CoroutineStart.UNDISPATCHED) {
            gate.awaitApproval(prompt)
        }
        val requestId = requireNotNull(gate.pending.value).requestId

        assertTrue(gate.resolve(requestId, approved = false))
        assertFalse(result.await())
        assertNull(gate.pending.value)
        assertFalse(gate.resolve(requestId, approved = true))
    }

    @Test
    fun cancellationClearsThePendingRequest() = runBlocking {
        val gate = AiToolApprovalGate()
        val result = async(start = CoroutineStart.UNDISPATCHED) {
            gate.awaitApproval(prompt)
        }
        val requestId = requireNotNull(gate.pending.value).requestId

        result.cancelAndJoin()

        assertNull(gate.pending.value)
        assertFalse(gate.resolve(requestId, approved = true))
    }

    @Test
    fun conversationInvalidationRejectsPendingApprovalImmediately() = runBlocking {
        val gate = AiToolApprovalGate()
        val result = async(start = CoroutineStart.UNDISPATCHED) {
            gate.awaitApproval(prompt)
        }
        val requestId = requireNotNull(gate.pending.value).requestId

        assertTrue(gate.rejectPending())
        assertFalse(result.await())
        assertNull(gate.pending.value)
        assertFalse(gate.resolve(requestId, approved = true))
    }

    @Test
    fun staleDecisionCannotResolveAReplacementApproval() = runBlocking {
        val gate = AiToolApprovalGate()
        val first = async(start = CoroutineStart.UNDISPATCHED) {
            gate.awaitApproval(prompt)
        }
        val firstRequestId = requireNotNull(gate.pending.value).requestId
        assertTrue(gate.resolve(firstRequestId, approved = false))
        assertFalse(first.await())

        val second = async(start = CoroutineStart.UNDISPATCHED) {
            gate.awaitApproval(prompt.copy(toolName = "calendar_update_event"))
        }
        val secondRequestId = requireNotNull(gate.pending.value).requestId

        assertTrue(secondRequestId > firstRequestId)
        assertFalse(gate.resolve(firstRequestId, approved = true))
        assertTrue(gate.resolve(secondRequestId, approved = true))
        assertTrue(second.await())
    }

    @Test
    fun approvalPromptReceivesOnlyValidatedArgumentNames() {
        val names = approvalArgumentNames("""{"title":"private event","token":"secret-value"}""")

        assertEquals(listOf("title", "token"), names)
        assertFalse(requireNotNull(names).any { it.contains("secret") })
    }

    @Test
    fun approvalArgumentNamesRejectMalformedOrExcessiveArguments() {
        assertNull(approvalArgumentNames("{invalid"))
        val tooMany = buildString {
            append('{')
            repeat(17) { index ->
                if (index > 0) append(',')
                val field = index + 1
                append('"').append("field").append(field).append('"')
                append(':').append(field)
            }
            append('}')
        }
        assertNull(approvalArgumentNames(tooMany))
    }

    @Test
    fun displayTextRemovesControlAndBidiOverrideCharacters() {
        val untrusted = "Calendar" + 0x202E.toChar() + "\nCreate event"
        assertEquals("Calendar Create event", sanitizeApprovalDisplayText(untrusted, 128))
    }

    @Test
    fun queuedApprovalIsSkippedWhenItsConversationBecomesStale() = runBlocking {
        val gate = AiToolApprovalGate()
        var currentConversation = true
        val first = async(start = CoroutineStart.UNDISPATCHED) {
            gate.awaitApproval(prompt) { currentConversation }
        }
        val second = async(start = CoroutineStart.UNDISPATCHED) {
            gate.awaitApproval(prompt.copy(toolName = "calendar_delete_event")) {
                currentConversation
            }
        }

        assertEquals(prompt.toolName, gate.pending.value?.toolName)
        currentConversation = false
        assertTrue(gate.rejectPending())
        assertFalse(first.await())
        assertFalse(second.await())
        assertNull(gate.pending.value)
    }

    @Test
    fun concurrentRequestsArePresentedOneAtATime() = runBlocking {
        val gate = AiToolApprovalGate()
        val secondPrompt = prompt.copy(toolName = "calendar_delete_event")
        val first = async(start = CoroutineStart.UNDISPATCHED) {
            gate.awaitApproval(prompt)
        }
        val firstRequestId = requireNotNull(gate.pending.value).requestId
        val second = async {
            gate.awaitApproval(secondPrompt)
        }

        assertEquals(prompt.toolName, gate.pending.value?.toolName)
        assertTrue(gate.resolve(firstRequestId, approved = false))
        assertFalse(first.await())
        val secondRequestId = requireNotNull(gate.pending.value).requestId
        assertEquals(secondPrompt.toolName, gate.pending.value?.toolName)
        assertTrue(gate.resolve(secondRequestId, approved = true))
        assertTrue(second.await())
        assertNull(gate.pending.value)
    }
}
