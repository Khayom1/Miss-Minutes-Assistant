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

        assertEquals(prompt, gate.pending.value)
        assertTrue(gate.resolve(approved = true))
        assertTrue(result.await())
        assertNull(gate.pending.value)
        assertFalse(gate.resolve(approved = true))
    }

    @Test
    fun rejectionIsReturnedToTheToolExecutor() = runBlocking {
        val gate = AiToolApprovalGate()
        val result = async(start = CoroutineStart.UNDISPATCHED) {
            gate.awaitApproval(prompt)
        }

        assertTrue(gate.resolve(approved = false))
        assertFalse(result.await())
        assertNull(gate.pending.value)
    }

    @Test
    fun cancellationClearsThePendingRequest() = runBlocking {
        val gate = AiToolApprovalGate()
        val result = async(start = CoroutineStart.UNDISPATCHED) {
            gate.awaitApproval(prompt)
        }

        result.cancelAndJoin()

        assertNull(gate.pending.value)
        assertFalse(gate.resolve(approved = true))
    }

    @Test
    fun concurrentRequestsArePresentedOneAtATime() = runBlocking {
        val gate = AiToolApprovalGate()
        val secondPrompt = prompt.copy(toolName = "calendar_delete_event")
        val first = async(start = CoroutineStart.UNDISPATCHED) {
            gate.awaitApproval(prompt)
        }
        val second = async {
            gate.awaitApproval(secondPrompt)
        }

        assertEquals(prompt, gate.pending.value)
        assertTrue(gate.resolve(approved = false))
        assertFalse(first.await())
        assertEquals(secondPrompt, gate.pending.value)
        assertTrue(gate.resolve(approved = true))
        assertTrue(second.await())
        assertNull(gate.pending.value)
    }
}
