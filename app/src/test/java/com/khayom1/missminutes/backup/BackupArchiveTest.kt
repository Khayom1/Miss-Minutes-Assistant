package com.khayom1.missminutes.backup

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Test
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

class BackupArchiveTest {
    private val archive = BackupArchive.instance
    private val password = "correct-horse-battery".toCharArray()

    @Test
    fun encryptedArchiveRoundTripsOnlySelectedCategories() {
        val chat = buildJsonObject { put("conversationCount", 3) }
        val settings = buildJsonObject { put("darkMode", true) }
        val bytes = archive.encode(
            mapOf(
                BackupArchive.Category.CHAT_HISTORY to chat,
                BackupArchive.Category.APP_SETTINGS to settings
            ),
            password,
            exportedAtEpochMillis = 1234L
        )

        assertFalse(bytes.toString(Charsets.ISO_8859_1).contains("conversationCount"))
        val decoded = archive.decode(bytes, password)
        assertEquals(setOf(BackupArchive.Category.CHAT_HISTORY, BackupArchive.Category.APP_SETTINGS), decoded.preview.categories)
        assertEquals(1234L, decoded.preview.exportedAtEpochMillis)
        assertEquals(chat, decoded.payloads[BackupArchive.Category.CHAT_HISTORY])
        assertEquals(settings, decoded.payloads[BackupArchive.Category.APP_SETTINGS])
    }

    @Test
    fun wrongPasswordIsRejected() {
        val bytes = archive.encode(
            mapOf(BackupArchive.Category.PLUGINS to buildJsonObject { put("plugins", 0) }),
            password
        )
        assertThrows(IllegalArgumentException::class.java) {
            archive.decode(bytes, "wrong-password-123".toCharArray())
        }
    }

    @Test
    fun tamperingIsRejected() {
        val bytes = archive.encode(
            mapOf(BackupArchive.Category.APP_SETTINGS to buildJsonObject { put("theme", "dark") }),
            password
        )
        bytes[bytes.lastIndex] = (bytes.last().toInt() xor 1).toByte()
        assertThrows(IllegalArgumentException::class.java) { archive.decode(bytes, password) }
    }

    @Test
    fun emptyCategorySelectionIsRejected() {
        assertThrows(IllegalArgumentException::class.java) { archive.encode(emptyMap(), password) }
    }

    @Test
    fun shortPasswordIsRejected() {
        assertThrows(IllegalArgumentException::class.java) {
            archive.encode(mapOf(BackupArchive.Category.PLUGINS to buildJsonObject {}), "short".toCharArray())
        }
    }
}
