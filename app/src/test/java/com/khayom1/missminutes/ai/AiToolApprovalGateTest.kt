package com.khayom1.missminutes.ai

import com.khayom1.missminutes.plugins.PluginFieldSpec
import com.khayom1.missminutes.plugins.PluginValueType
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
        toolName = "create_reminder",
        pluginName = "Android device tools",
        description = "Create a calendar reminder.",
        argumentPreview = listOf("title: dentist visit", "startEpochMillis: 12345")
    )

    @Test
    fun approvalResolvesAndClearsPendingPrompt() = runBlocking {
        val gate = AiToolApprovalGate()
        val result = async(start = CoroutineStart.UNDISPATCHED) { gate.awaitApproval(prompt) }
        val requestId = requireNotNull(gate.pending.value).requestId
        assertTrue(gate.resolve(requestId, approved = true))
        assertTrue(result.await())
        assertNull(gate.pending.value)
        assertFalse(gate.resolve(requestId, approved = true))
    }

    @Test
    fun rejectionIsReturnedToTheToolExecutor() = runBlocking {
        val gate = AiToolApprovalGate()
        val result = async(start = CoroutineStart.UNDISPATCHED) { gate.awaitApproval(prompt) }
        val requestId = requireNotNull(gate.pending.value).requestId
        assertTrue(gate.resolve(requestId, approved = false))
        assertFalse(result.await())
        assertNull(gate.pending.value)
        assertFalse(gate.resolve(requestId, approved = true))
    }

    @Test
    fun cancellationClearsThePendingRequest() = runBlocking {
        val gate = AiToolApprovalGate()
        val result = async(start = CoroutineStart.UNDISPATCHED) { gate.awaitApproval(prompt) }
        val requestId = requireNotNull(gate.pending.value).requestId
        result.cancelAndJoin()
        assertNull(gate.pending.value)
        assertFalse(gate.resolve(requestId, approved = true))
    }

    @Test
    fun conversationInvalidationRejectsPendingApprovalImmediately() = runBlocking {
        val gate = AiToolApprovalGate()
        val result = async(start = CoroutineStart.UNDISPATCHED) { gate.awaitApproval(prompt) }
        val requestId = requireNotNull(gate.pending.value).requestId
        assertTrue(gate.rejectPending())
        assertFalse(result.await())
        assertNull(gate.pending.value)
        assertFalse(gate.resolve(requestId, approved = true))
    }

    @Test
    fun staleDecisionCannotResolveAReplacementApproval() = runBlocking {
        val gate = AiToolApprovalGate()
        val first = async(start = CoroutineStart.UNDISPATCHED) { gate.awaitApproval(prompt) }
        val firstRequestId = requireNotNull(gate.pending.value).requestId
        assertTrue(gate.resolve(firstRequestId, approved = false))
        assertFalse(first.await())
        val second = async(start = CoroutineStart.UNDISPATCHED) {
            gate.awaitApproval(prompt.copy(toolName = "set_alarm"))
        }
        val secondRequestId = requireNotNull(gate.pending.value).requestId
        assertTrue(secondRequestId > firstRequestId)
        assertFalse(gate.resolve(firstRequestId, approved = true))
        assertTrue(gate.resolve(secondRequestId, approved = true))
        assertTrue(second.await())
    }

    @Test
    fun queuedApprovalIsSkippedWhenItsConversationBecomesStale() = runBlocking {
        val gate = AiToolApprovalGate()
        var currentConversation = true
        val first = async(start = CoroutineStart.UNDISPATCHED) {
            gate.awaitApproval(prompt) { currentConversation }
        }
        val second = async(start = CoroutineStart.UNDISPATCHED) {
            gate.awaitApproval(prompt.copy(toolName = "set_alarm")) { currentConversation }
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
        val first = async(start = CoroutineStart.UNDISPATCHED) { gate.awaitApproval(prompt) }
        val firstRequestId = requireNotNull(gate.pending.value).requestId
        val second = async { gate.awaitApproval(prompt.copy(toolName = "set_alarm")) }
        assertTrue(gate.resolve(firstRequestId, approved = false))
        assertFalse(first.await())
        val secondRequestId = requireNotNull(gate.pending.value).requestId
        assertEquals("set_alarm", gate.pending.value?.toolName)
        assertTrue(gate.resolve(secondRequestId, approved = true))
        assertTrue(second.await())
        assertNull(gate.pending.value)
    }

    @Test
    fun approvalExpiresAndClearsPendingPrompt() = runBlocking {
        val gate = AiToolApprovalGate(approvalTimeoutMillis = 100L)
        val result = async(start = CoroutineStart.UNDISPATCHED) { gate.awaitApproval(prompt) }
        val requestId = requireNotNull(gate.pending.value).requestId
        assertFalse(result.await())
        assertNull(gate.pending.value)
        assertFalse(gate.resolve(requestId, approved = true))
    }

    @Test
    fun previewValidatesSchemaAndRedactsSensitiveFields() {
        val schema = mapOf(
            "title" to PluginFieldSpec("title", PluginValueType.STRING, maxLength = 64),
            "apiToken" to PluginFieldSpec("apiToken", PluginValueType.STRING, maxLength = 128)
        )
        val preview = approvalArgumentPreview(
            """{"title":"dentist visit","apiToken":"secret-token-value"}""",
            schema
        )
        assertEquals(listOf("apiToken: [redacted]", "title: dentist visit"), preview)
    }

    @Test
    fun previewRejectsMalformedUnknownAndWronglyTypedArguments() {
        val schema = mapOf(
            "title" to PluginFieldSpec("title", PluginValueType.STRING, maxLength = 64),
            "count" to PluginFieldSpec("count", PluginValueType.INTEGER, minimum = 1, maximum = 3)
        )
        assertNull(approvalArgumentPreview("{invalid", schema))
        assertNull(approvalArgumentPreview("""{"shell":"id"}""", schema))
        assertNull(approvalArgumentPreview("""{"title":false}""", schema))
        assertNull(approvalArgumentPreview("""{"count":"2"}""", schema))
        assertNull(approvalArgumentPreview("""{"count":4}""", schema))
    }

    @Test
    fun displayTextRemovesControlAndBidiOverrideCharacters() {
        val untrusted = "Calendar" + 0x202E.toChar() + "\nCreate event"
        assertEquals("Calendar Create event", sanitizeApprovalDisplayText(untrusted, 128))
    }
}
