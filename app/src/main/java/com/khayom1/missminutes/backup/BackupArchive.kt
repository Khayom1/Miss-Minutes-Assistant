package com.khayom1.missminutes.backup

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.security.SecureRandom
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * Password-encrypted, versioned backup container.
 *
 * The encrypted payload is a bounded ZIP containing a manifest and independently
 * selectable JSON category files. Credentials and Android permission grants are
 * intentionally not part of this format.
 */
class BackupArchive private constructor() {
    enum class Category(val fileName: String) {
        CHAT_HISTORY("chat-history.json"),
        APP_SETTINGS("app-settings.json"),
        PLUGINS("plugins.json")
    }

    data class Preview(
        val schemaVersion: Int,
        val exportedAtEpochMillis: Long,
        val categories: Set<Category>,
        val uncompressedBytes: Int
    )

    data class Decoded(
        val preview: Preview,
        val payloads: Map<Category, JsonObject>
    )

    fun encode(
        payloads: Map<Category, JsonObject>,
        password: CharArray,
        exportedAtEpochMillis: Long = System.currentTimeMillis()
    ): ByteArray {
        require(password.size >= MIN_PASSWORD_CHARS) { "Backup password is too short." }
        require(payloads.isNotEmpty()) { "Select at least one backup category." }
        require(payloads.size <= Category.entries.size)
        val zipBytes = createZip(payloads, exportedAtEpochMillis)
        require(zipBytes.size <= MAX_ARCHIVE_BYTES) { "Backup is too large." }

        val salt = ByteArray(SALT_BYTES).also(random::nextBytes)
        val iv = ByteArray(IV_BYTES).also(random::nextBytes)
        val key = deriveKey(password, salt)
        return try {
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.ENCRYPT_MODE, key, GCMParameterSpec(TAG_BITS, iv))
            cipher.updateAAD(AAD)
            val encrypted = cipher.doFinal(zipBytes)
            ByteArrayOutputStream().use { output ->
                DataOutputStream(output).use { data ->
                    data.write(MAGIC)
                    data.writeInt(CONTAINER_VERSION)
                    data.write(salt)
                    data.write(iv)
                    data.writeInt(encrypted.size)
                    data.write(encrypted)
                }
                output.toByteArray()
            }
        } finally {
            zipBytes.fill(0)
        }
    }

    fun decode(archive: ByteArray, password: CharArray): Decoded {
        require(password.size >= MIN_PASSWORD_CHARS) { "Backup password is too short." }
        require(archive.size in HEADER_BYTES + TAG_BYTES..MAX_ARCHIVE_BYTES) {
            "Backup archive size is invalid."
        }
        val plaintext = try {
            DataInputStream(ByteArrayInputStream(archive)).use { data ->
                val magic = ByteArray(MAGIC.size)
                data.readFully(magic)
                require(magic.contentEquals(MAGIC)) { "This is not a Miss Minutes backup." }
                require(data.readInt() == CONTAINER_VERSION) { "Unsupported backup container version." }
                val salt = ByteArray(SALT_BYTES).also(data::readFully)
                val iv = ByteArray(IV_BYTES).also(data::readFully)
                val encryptedLength = data.readInt()
                require(encryptedLength == data.available() && encryptedLength >= TAG_BYTES) {
                    "Backup archive is truncated or contains trailing data."
                }
                val encrypted = ByteArray(encryptedLength).also(data::readFully)
                val cipher = Cipher.getInstance(TRANSFORMATION)
                cipher.init(Cipher.DECRYPT_MODE, deriveKey(password, salt), GCMParameterSpec(TAG_BITS, iv))
                cipher.updateAAD(AAD)
                cipher.doFinal(encrypted)
            }
        } catch (error: Exception) {
            if (error is IllegalArgumentException) throw error
            throw IllegalArgumentException("Unable to decrypt or authenticate backup. Check the password and file.", error)
        }

        return try {
            require(plaintext.size <= MAX_ARCHIVE_BYTES) { "Backup contents exceed the size limit." }
            readZip(plaintext)
        } finally {
            plaintext.fill(0)
        }
    }

    private fun createZip(payloads: Map<Category, JsonObject>, exportedAt: Long): ByteArray {
        val manifest = buildJsonObject {
            put("schemaVersion", CURRENT_SCHEMA_VERSION)
            put("exportedAtEpochMillis", exportedAt)
            put("categories", kotlinx.serialization.json.buildJsonArray {
                payloads.keys.sortedBy { it.name }.forEach { add(kotlinx.serialization.json.JsonPrimitive(it.name)) }
            })
        }.toString().toByteArray(Charsets.UTF_8)
        val output = ByteArrayOutputStream()
        try {
            ZipOutputStream(output).use { zip ->
                zip.putNextEntry(ZipEntry(MANIFEST_ENTRY))
                zip.write(manifest)
                zip.closeEntry()
                payloads.toSortedMap(compareBy { it.name }).forEach { (category, json) ->
                    val bytes = json.toString().toByteArray(Charsets.UTF_8)
                    try {
                        require(bytes.size <= MAX_CATEGORY_BYTES) { "A backup category is too large." }
                        zip.putNextEntry(ZipEntry(category.fileName))
                        zip.write(bytes)
                        zip.closeEntry()
                    } finally {
                        bytes.fill(0)
                    }
                }
            }
            return output.toByteArray()
        } finally {
            manifest.fill(0)
        }
    }

    private fun readZip(bytes: ByteArray): Decoded {
        val entries = linkedMapOf<String, ByteArray>()
        try {
            var total = 0
            ZipInputStream(ByteArrayInputStream(bytes)).use { zip ->
                while (true) {
                    val entry = zip.nextEntry ?: break
                    require(!entry.isDirectory && !entry.name.startsWith("/") &&
                        !entry.name.contains("..") && entry.name !in entries) {
                        "Backup contains an unsafe or duplicate entry."
                    }
                    require(entry.name == MANIFEST_ENTRY || Category.entries.any { it.fileName == entry.name }) {
                        "Backup contains an unknown entry."
                    }
                    val content = ByteArrayOutputStream()
                    val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                    while (true) {
                        val count = zip.read(buffer)
                        if (count < 0) break
                        total += count
                        require(total <= MAX_UNCOMPRESSED_BYTES) { "Backup expands beyond the safety limit." }
                        require(content.size() + count <= MAX_CATEGORY_BYTES) { "Backup entry is too large." }
                        content.write(buffer, 0, count)
                    }
                    entries[entry.name] = content.toByteArray()
                    zip.closeEntry()
                }
            }

            val manifestBytes = entries.remove(MANIFEST_ENTRY)
                ?: throw IllegalArgumentException("Backup manifest is missing.")
            try {
                val manifest = Json.parseToJsonElement(manifestBytes.toString(Charsets.UTF_8)).jsonObject
                val version = manifest["schemaVersion"]?.jsonPrimitive?.content?.toIntOrNull()
                    ?: throw IllegalArgumentException("Backup schema version is missing.")
                require(version in 1..CURRENT_SCHEMA_VERSION) {
                    "This backup was created by a newer app version."
                }
                val exportedAt = manifest["exportedAtEpochMillis"]?.jsonPrimitive?.content?.toLongOrNull()
                    ?: throw IllegalArgumentException("Backup timestamp is invalid.")
                val declared = manifest["categories"]?.jsonArray?.map {
                    runCatching { Category.valueOf(it.jsonPrimitive.content) }
                        .getOrElse { throw IllegalArgumentException("Backup category is invalid.") }
                }?.toSet() ?: throw IllegalArgumentException("Backup category list is missing.")
                val actual = entries.keys.map { file ->
                    Category.entries.single { it.fileName == file }
                }.toSet()
                require(declared.isNotEmpty() && declared == actual) {
                    "Backup manifest does not match its category files."
                }
                val payloads = actual.associateWith { category ->
                    Json.parseToJsonElement(
                        entries.getValue(category.fileName).toString(Charsets.UTF_8)
                    ).jsonObject
                }
                return Decoded(Preview(version, exportedAt, actual, total), payloads)
            } finally {
                manifestBytes.fill(0)
            }
        } finally {
            entries.values.forEach { it.fill(0) }
        }
    }

    private fun deriveKey(password: CharArray, salt: ByteArray): SecretKeySpec {
        val spec = PBEKeySpec(password, salt, PBKDF2_ITERATIONS, KEY_BITS)
        return try {
            val bytes = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
                .generateSecret(spec).encoded
            SecretKeySpec(bytes, "AES").also { bytes.fill(0) }
        } finally {
            spec.clearPassword()
        }
    }

    companion object {
        private const val CONTAINER_VERSION = 1
        const val CURRENT_SCHEMA_VERSION = 1
        private const val PBKDF2_ITERATIONS = 210_000
        private const val KEY_BITS = 256
        private const val SALT_BYTES = 16
        private const val IV_BYTES = 12
        private const val TAG_BITS = 128
        private const val TAG_BYTES = TAG_BITS / 8
        private const val MIN_PASSWORD_CHARS = 10
        private const val MAX_CATEGORY_BYTES = 2_000_000
        private const val MAX_UNCOMPRESSED_BYTES = 4_000_000
        private const val MAX_ARCHIVE_BYTES = 4_500_000
        private const val MANIFEST_ENTRY = "manifest.json"
        private const val TRANSFORMATION = "AES/GCM/NoPadding"
        private val MAGIC = byteArrayOf(0x4d, 0x4d, 0x42, 0x4b, 0x41, 0x52, 0x43, 0x48)
        private val AAD = "com.khayom1.missminutes.backup.container.v1".toByteArray(Charsets.UTF_8)
        private val random = SecureRandom()
        private const val HEADER_BYTES = 8 + 4 + SALT_BYTES + IV_BYTES + 4
        private const val MAX_ARCHIVE_BYTES_PLACEHOLDER = 0
        val instance = BackupArchive()
    }
}
