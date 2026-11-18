package com.khayom1.missminutes.ai

import java.io.File
import java.util.UUID
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LocalModelStorePathTest {
    @Test
    fun generatedModelPathsAreUniqueAndKeepTheRequiredExtension() {
        val directory = File("models")
        val firstId = UUID.fromString("00000000-0000-0000-0000-000000000001")
        val secondId = UUID.fromString("00000000-0000-0000-0000-000000000002")

        val firstTemporary = localModelTemporaryFile(directory, firstId)
        val secondTemporary = localModelTemporaryFile(directory, secondId)
        val firstDestination = localModelDestinationFile(directory, firstId)
        val secondDestination = localModelDestinationFile(directory, secondId)

        assertNotEquals(firstTemporary.name, secondTemporary.name)
        assertNotEquals(firstDestination.name, secondDestination.name)
        assertTrue(firstTemporary.name.startsWith("."))
        assertTrue(firstTemporary.name.endsWith(".part"))
        assertEquals("$firstId.litertlm", firstDestination.name)
        assertEquals("$secondId.litertlm", secondDestination.name)
    }

    @Test
    fun localToolCallIdsMatchDispatcherValidation() {
        val id = newLocalToolCallId(
            UUID.fromString("00000000-0000-0000-0000-000000000001")
        )

        assertEquals("local_00000000000000000000000000000001", id)
        assertTrue(id.matches(Regex("^[a-zA-Z0-9_-]{1,128}$")))
    }

    @Test
    fun localPromptIncludesActualConversationTextAndToolNames() {
        val prompt = buildLiteRtLmPrompt(
            listOf(
                AiMessage(AiRole.USER, "Read my private local note."),
                AiMessage(AiRole.ASSISTANT, "I will inspect it."),
                AiMessage(AiRole.TOOL, "The note says hello.", name = "notes_read")
            )
        )

        assertTrue(prompt.contains("User:\nRead my private local note."))
        assertTrue(prompt.contains("Assistant:\nI will inspect it."))
        assertTrue(prompt.contains("Tool result (notes_read):\nThe note says hello."))
    }
}
