package com.khayom1.missminutes.ai

import java.io.File
import java.nio.file.Files
import java.util.UUID
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
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
    fun canonicalModelPathRejectsOutsideTraversalAndSymlinkTargets() {
        val directory = Files.createTempDirectory("miss-minutes-models").toFile()
        val inside = File(directory, "supported.litertlm").apply { writeText("model") }
        val outside = File(directory.parentFile, "${directory.name}-outside.litertlm")
            .apply { writeText("outside") }
        try {
            assertEquals(
                inside.canonicalPath,
                canonicalLocalModelFile(directory, inside.absolutePath)?.absolutePath
            )
            assertNull(canonicalLocalModelFile(directory, outside.absolutePath))
            assertNull(canonicalLocalModelFile(directory, File(directory, "../${outside.name}").path))

            val link = File(directory, "linked.litertlm")
            Files.createSymbolicLink(link.toPath(), outside.toPath())
            assertNull(canonicalLocalModelFile(directory, link.absolutePath))
        } finally {
            directory.deleteRecursively()
            outside.delete()
        }
    }

    @Test
    fun modelReadinessRequiresValidatedSizeAndTimestamp() {
        val directory = Files.createTempDirectory("miss-minutes-model-validation").toFile()
        val model = File(directory, "ready.litertlm").apply { writeBytes(byteArrayOf(1, 2, 3, 4)) }
        try {
            val size = model.length()
            val modified = model.lastModified()
            assertEquals(
                model.canonicalPath,
                validatedLocalModelFile(directory, model.absolutePath, size, modified)?.absolutePath
            )
            assertNull(validatedLocalModelFile(directory, model.absolutePath, size + 1, modified))
            assertNull(validatedLocalModelFile(directory, model.absolutePath, size, modified + 1))
            assertNull(validatedLocalModelFile(directory, model.absolutePath, -1, modified))
        } finally {
            directory.deleteRecursively()
        }
    }
}
