package com.khayom1.missminutes.backup

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import com.khayom1.missminutes.plugins.PluginManifest
import com.khayom1.missminutes.plugins.PluginManifestArchive

/**
 * Converts the app's existing local-store export and strict category codecs into
 * the encrypted BackupArchive container. This class never accepts credentials,
 * Android permission grants or plugin consent state.
 */
object BackupPayloadAdapters {
    data class RestoredPayloads(
        val conversationExportJson: String?,
        val settings: SettingsBackupAdapter.Settings?,
        val pluginManifests: List<PluginManifest>?
    )

    fun encode(
        selected: Set<BackupArchive.Category>,
        conversationExportJson: String?,
        settings: SettingsBackupAdapter.Settings?,
        pluginManifests: List<PluginManifest>,
        conversationRetentionDays: Int = 90
    ): Map<BackupArchive.Category, JsonObject> {
        require(selected.isNotEmpty()) { "Select at least one backup category." }
        val result = linkedMapOf<BackupArchive.Category, JsonObject>()
        if (BackupArchive.Category.CHAT_HISTORY in selected) {
            val raw = requireNotNull(conversationExportJson) { "Conversation data is unavailable." }
            val chat = normalizeConversationPayload(raw, conversationRetentionDays)
            validateConversationPayload(chat)
            result[BackupArchive.Category.CHAT_HISTORY] = chat
        }
        if (BackupArchive.Category.APP_SETTINGS in selected) {
            result[BackupArchive.Category.APP_SETTINGS] = SettingsBackupAdapter.encode(
                requireNotNull(settings) { "App settings are unavailable." }
            )
        }
        if (BackupArchive.Category.PLUGINS in selected) {
            result[BackupArchive.Category.PLUGINS] = PluginManifestArchive.encode(pluginManifests)
        }
        return result
    }

    fun decode(decoded: BackupArchive.Decoded): RestoredPayloads {
        val chat = decoded.payloads[BackupArchive.Category.CHAT_HISTORY]
        chat?.let(::validateConversationPayload)
        val settings = decoded.payloads[BackupArchive.Category.APP_SETTINGS]?.let(
            SettingsBackupAdapter::decode
        )
        val plugins = decoded.payloads[BackupArchive.Category.PLUGINS]?.let(
            PluginManifestArchive::decode
        )
        require(decoded.preview.categories == decoded.payloads.keys) {
            "Backup preview does not match its decoded categories."
        }
        return RestoredPayloads(chat?.toString(), settings, plugins)
    }

    private fun normalizeConversationPayload(raw: String, retentionDays: Int): JsonObject {
        require(retentionDays in setOf(0, 30, 90, 365))
        val source = Json.parseToJsonElement(raw).jsonObject
        if ("conversations" in source) return source
        require(source.keys == setOf("schemaVersion", "exportedAt", "conversation")) {
            "Selected conversation backup contains unknown or missing fields."
        }
        val conversation = source["conversation"]?.jsonObject
            ?: throw IllegalArgumentException("Selected conversation is missing.")
        require(conversation.keys == setOf("id", "title", "createdAt", "updatedAt", "messages"))
        return kotlinx.serialization.json.buildJsonObject {
            put("schemaVersion", 2)
            put("exportedAt", source["exportedAt"] ?: throw IllegalArgumentException("Export timestamp is missing."))
            put("retentionDays", retentionDays)
            put("conversations", kotlinx.serialization.json.buildJsonArray { add(conversation) })
            put("memory", kotlinx.serialization.json.buildJsonArray {})
        }
    }

    private fun validateConversationPayload(payload: JsonObject) {
        require(payload.keys == setOf(
            "schemaVersion", "exportedAt", "retentionDays", "conversations", "memory"
        )) { "Conversation backup contains unknown or missing fields." }
        val version = payload["schemaVersion"]?.jsonPrimitive?.content?.toIntOrNull()
            ?: throw IllegalArgumentException("Conversation backup schema is invalid.")
        require(version in 1..2) { "Unsupported conversation backup version." }
        require(payload["exportedAt"]?.jsonPrimitive?.content?.toLongOrNull() != null)
        val retentionDays = payload["retentionDays"]?.jsonPrimitive?.content?.toIntOrNull()
            ?: throw IllegalArgumentException("Conversation retention setting is invalid.")
        require(retentionDays in setOf(0, 30, 90, 365))
        val conversations = payload["conversations"]?.jsonArray
            ?: throw IllegalArgumentException("Conversation list is missing.")
        val memories = payload["memory"]?.jsonArray
            ?: throw IllegalArgumentException("Memory list is missing.")
        require(conversations.size <= 1_000 && memories.size <= 1_000)
    }
}
