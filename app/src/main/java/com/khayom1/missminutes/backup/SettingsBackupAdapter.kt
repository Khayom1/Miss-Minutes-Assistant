package com.khayom1.missminutes.backup

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

/** Strict allowlist: no API keys, tokens, permissions or plugin consent. */
object SettingsBackupAdapter {
    private const val SCHEMA_VERSION = 1
    private val allowedKeys = setOf("schemaVersion", "language", "darkMode", "retentionDays", "memoryContextEnabled")

    data class Settings(
        val language: String,
        val darkMode: Boolean,
        val retentionDays: Int,
        val memoryContextEnabled: Boolean
    )

    fun encode(settings: Settings): JsonObject {
        validate(settings)
        return buildJsonObject {
            put("schemaVersion", SCHEMA_VERSION)
            put("language", settings.language)
            put("darkMode", settings.darkMode)
            put("retentionDays", settings.retentionDays)
            put("memoryContextEnabled", settings.memoryContextEnabled)
        }
    }

    fun decode(payload: JsonObject): Settings {
        require(payload.keys == allowedKeys) { "Unknown or missing settings fields." }
        require(payload["schemaVersion"]?.jsonPrimitive?.content?.toIntOrNull() == SCHEMA_VERSION) {
            "Unsupported settings backup version."
        }
        val value = Settings(
            language = payload["language"]?.jsonPrimitive?.content ?: error("Language is missing."),
            darkMode = payload["darkMode"]?.jsonPrimitive?.content?.toBooleanStrictOrNull()
                ?: error("Dark mode value is invalid."),
            retentionDays = payload["retentionDays"]?.jsonPrimitive?.content?.toIntOrNull()
                ?: error("Retention value is invalid."),
            memoryContextEnabled = payload["memoryContextEnabled"]?.jsonPrimitive?.content?.toBooleanStrictOrNull()
                ?: error("Memory setting is invalid.")
        )
        validate(value)
        return value
    }

    private fun validate(value: Settings) {
        require(value.language in setOf("en", "ru", "tg")) { "Unsupported language." }
        require(value.retentionDays in setOf(0, 30, 90, 365)) { "Unsupported retention period." }
    }
}
