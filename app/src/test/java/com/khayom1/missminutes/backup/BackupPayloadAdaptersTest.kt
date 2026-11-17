package com.khayom1.missminutes.backup

import com.khayom1.missminutes.plugins.PluginCapability
import com.khayom1.missminutes.plugins.PluginManifest
import com.khayom1.missminutes.plugins.PluginActionSpec
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Test

class BackupPayloadAdaptersTest {
    private val chat = buildJsonObject {
        put("schemaVersion", 2)
        put("exportedAt", 1234L)
        put("retentionDays", 90)
        put("conversations", kotlinx.serialization.json.buildJsonArray {})
        put("memory", kotlinx.serialization.json.buildJsonArray {})
    }
    private val settings = SettingsBackupAdapter.Settings("tg", true, 90, false)
    private val plugin = PluginManifest(
        id = "archive_test",
        version = "1.0.0",
        displayName = "Archive test",
        description = "Declarative only",
        capabilities = setOf(PluginCapability.DEVICE_INFORMATION),
        actions = listOf(PluginActionSpec("read_info", PluginCapability.DEVICE_INFORMATION))
    )

    @Test
    fun selectedCategoriesAreEncodedAndRoundTripThroughEncryptedArchive() {
        val selected = setOf(
            BackupArchive.Category.CHAT_HISTORY,
            BackupArchive.Category.APP_SETTINGS,
            BackupArchive.Category.PLUGINS
        )
        val payloads = BackupPayloadAdapters.encode(selected, chat.toString(), settings, listOf(plugin))
        val bytes = BackupArchive.instance.encode(payloads, "correct-horse-battery".toCharArray(), 1234L)
        val restored = BackupPayloadAdapters.decode(
            BackupArchive.instance.decode(bytes, "correct-horse-battery".toCharArray())
        )
        assertEquals(chat.toString(), restored.conversationExportJson)
        assertEquals(settings, restored.settings)
        assertEquals(listOf(plugin), restored.pluginManifests)
    }

    @Test
    fun omittedCategoriesStayOmitted() {
        val payloads = BackupPayloadAdapters.encode(
            setOf(BackupArchive.Category.APP_SETTINGS), null, settings, emptyList()
        )
        val bytes = BackupArchive.instance.encode(payloads, "correct-horse-battery".toCharArray())
        val restored = BackupPayloadAdapters.decode(
            BackupArchive.instance.decode(bytes, "correct-horse-battery".toCharArray())
        )
        assertNull(restored.conversationExportJson)
        assertNull(restored.pluginManifests)
        assertEquals(settings, restored.settings)
    }

    @Test
    fun selectedConversationIsNormalizedForRestore() {
        val selected = buildJsonObject {
            put("schemaVersion", 2)
            put("exportedAt", 1234L)
            put("conversation", buildJsonObject {
                put("id", "conversation-1")
                put("title", "Selected")
                put("createdAt", 100L)
                put("updatedAt", 200L)
                put("messages", kotlinx.serialization.json.buildJsonArray {})
            })
        }
        val payloads = BackupPayloadAdapters.encode(
            setOf(BackupArchive.Category.CHAT_HISTORY),
            selected.toString(),
            null,
            emptyList(),
            30
        )
        val restored = BackupPayloadAdapters.decode(
            BackupArchive.instance.decode(
                BackupArchive.instance.encode(payloads, "correct-horse-battery".toCharArray()),
                "correct-horse-battery".toCharArray()
            )
        )
        val normalized = kotlinx.serialization.json.Json.parseToJsonElement(
            restored.conversationExportJson!!
        ).jsonObject
        assertEquals(1, normalized.getValue("conversations").jsonArray.size)
        assertEquals(30, normalized.getValue("retentionDays").jsonPrimitive.content.toInt())
    }

    @Test
    fun rejectsMalformedConversationPayloadBeforeEncryption() {
        assertThrows(IllegalArgumentException::class.java) {
            BackupPayloadAdapters.encode(
                setOf(BackupArchive.Category.CHAT_HISTORY),
                """{"schemaVersion":2,"apiKey":"secret"}""",
                null,
                emptyList()
            )
        }
    }

    @Test
    fun rejectsEmptySelection() {
        assertThrows(IllegalArgumentException::class.java) {
            BackupPayloadAdapters.encode(emptySet(), null, null, emptyList())
        }
    }
}
