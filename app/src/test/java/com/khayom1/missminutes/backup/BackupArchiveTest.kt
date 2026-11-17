package com.khayom1.missminutes.backup

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.io.DataOutputStream
import java.security.SecureRandom
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec
import javax.crypto.spec.PBEKeySpec
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
    fun trailingContainerBytesAreRejected() {
        val valid = archive.encode(
            mapOf(BackupArchive.Category.APP_SETTINGS to buildJsonObject { put("darkMode", false) }),
            password
        )
        val withTrailingByte = valid + byteArrayOf(0x01)
        assertThrows(IllegalArgumentException::class.java) {
            archive.decode(withTrailingByte, password)
        }
    }

    @Test
    fun truncatedContainerIsRejectedBeforeDecryption() {
        val valid = archive.encode(
            mapOf(BackupArchive.Category.PLUGINS to buildJsonObject { put("count", 1) }),
            password
        )
        assertThrows(IllegalArgumentException::class.java) {
            archive.decode(valid.copyOf(valid.size - 1), password)
        }
    }

    @Test
    fun emptyCategorySelectionIsRejected() {
        assertThrows(IllegalArgumentException::class.java) { archive.encode(emptyMap(), password) }
    }

    @Test
    fun duplicateDeclaredCategoriesAreRejected() {
        val malformed = encryptRawZip(
            """{"schemaVersion":1,"exportedAtEpochMillis":1234,"categories":["APP_SETTINGS","APP_SETTINGS"]}""",
            listOf("app-settings.json" to "{}")
        )
        assertThrows(IllegalArgumentException::class.java) {
            archive.decode(malformed, password)
        }
    }

    @Test
    fun unknownManifestFieldsAreRejected() {
        val malformed = encryptRawZip(
            """{"schemaVersion":1,"exportedAtEpochMillis":1234,"categories":["APP_SETTINGS"],"untrusted":true}""",
            listOf("app-settings.json" to "{}")
        )
        assertThrows(IllegalArgumentException::class.java) {
            archive.decode(malformed, password)
        }
    }

    @Test
    fun unsafeZipEntryNamesAreRejected() {
        val malformed = encryptRawZip(
            """{"schemaVersion":1,"exportedAtEpochMillis":1234,"categories":["APP_SETTINGS"]}""",
            listOf("app-settings.json" to "{}", "../outside.json" to "{}")
        )
        assertThrows(IllegalArgumentException::class.java) {
            archive.decode(malformed, password)
        }
    }

    private fun encryptRawZip(
        manifest: String,
        entries: List<Pair<String, String>>
    ): ByteArray {
        val zipOutput = ByteArrayOutputStream()
        ZipOutputStream(zipOutput).use { zip ->
            zip.putNextEntry(ZipEntry("manifest.json"))
            zip.write(manifest.toByteArray(Charsets.UTF_8))
            zip.closeEntry()
            entries.forEach { (name, content) ->
                zip.putNextEntry(ZipEntry(name))
                zip.write(content.toByteArray(Charsets.UTF_8))
                zip.closeEntry()
            }
        }
        val zipBytes = zipOutput.toByteArray()
        val salt = ByteArray(16).also(SecureRandom()::nextBytes)
        val iv = ByteArray(12).also(SecureRandom()::nextBytes)
        val spec = PBEKeySpec(password, salt, 210_000, 256)
        val keyBytes = try {
            SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
                .generateSecret(spec).encoded
        } finally {
            spec.clearPassword()
        }
        val encrypted = try {
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(keyBytes, "AES"), GCMParameterSpec(128, iv))
            cipher.updateAAD("com.khayom1.missminutes.backup.container.v1".toByteArray(Charsets.UTF_8))
            cipher.doFinal(zipBytes)
        } finally {
            keyBytes.fill(0)
            zipBytes.fill(0)
        }
        val output = ByteArrayOutputStream()
        DataOutputStream(output).use { data ->
            data.write(byteArrayOf(0x4d, 0x4d, 0x42, 0x4b, 0x41, 0x52, 0x43, 0x48))
            data.writeInt(1)
            data.write(salt)
            data.write(iv)
            data.writeInt(encrypted.size)
            data.write(encrypted)
        }
        encrypted.fill(0)
        return output.toByteArray()
    }

    @Test
    fun shortPasswordIsRejected() {
        assertThrows(IllegalArgumentException::class.java) {
            archive.encode(mapOf(BackupArchive.Category.PLUGINS to buildJsonObject {}), "short".toCharArray())
        }
    }
}
