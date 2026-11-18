package com.khayom1.missminutes.backup

import com.khayom1.missminutes.ai.AiRuntimeProfile
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

/** Strict allowlist: no API keys, tokens, permissions or plugin consent. */
object SettingsBackupAdapter {
    private const val CURRENT_SCHEMA_VERSION = 2
    private val versionOneKeys = setOf(
        "schemaVersion", "language", "darkMode", "retentionDays", "memoryContextEnabled"
    )
    private val versionTwoKeys = versionOneKeys + "aiRuntimeProfile"

    data class Settings(
        val language: String,
        val darkMode: Boolean,
        val retentionDays: Int,
        val memoryContextEnabled: Boolean,
        val aiRuntimeProfile: String = AiRuntimeProfile.AUTO.name
    )

    fun encode(settings: Settings): JsonObject {
        validate(settings)
        return buildJsonObject {
            put("schemaVersion", CURRENT_SCHEMA_VERSION)
            put("language", settings.language)
            put("darkMode", settings.darkMode)
            put("retentionDays", settings.retentionDays)
            put("memoryContextEnabled", settings.memoryContextEnabled)
            put("aiRuntimeProfile", settings.aiRuntimeProfile)
        }
    }

    fun decode(payload: JsonObject): Settings {
        val version = payload["schemaVersion"]?.jsonPrimitive
            ?.takeIf { !it.isString }?.content?.toIntOrNull()
            ?: throw IllegalArgumentException("Settings backup schema version is invalid.")
        val expectedKeys = when (version) {
            1 -> versionOneKeys
            CURRENT_SCHEMA_VERSION -> versionTwoKeys
            else -> throw IllegalArgumentException("Unsupported settings backup version.")
        }
        require(payload.keys == expectedKeys) { "Unknown or missing settings fields." }

        val value = Settings(
            language = payload["language"]?.jsonPrimitive?.takeIf { it.isString }?.content
                ?: throw IllegalArgumentException("Language is missing."),
            darkMode = payload["darkMode"]?.jsonPrimitive?.takeIf { !it.isString }?.content?.toBooleanStrictOrNull()
                ?: throw IllegalArgumentException("Dark mode value is invalid."),
            retentionDays = payload["retentionDays"]?.jsonPrimitive?.takeIf { !it.isString }?.content?.toIntOrNull()
                ?: throw IllegalArgumentException("Retention value is invalid."),
            memoryContextEnabled = payload["memoryContextEnabled"]?.jsonPrimitive?.takeIf { !it.isString }?.content?.toBooleanStrictOrNull()
                ?: throw IllegalArgumentException("Memory setting is invalid."),
            aiRuntimeProfile = if (version == 1) {
                AiRuntimeProfile.AUTO.name
            } else {
                payload["aiRuntimeProfile"]?.jsonPrimitive?.takeIf { it.isString }?.content
                    ?: throw IllegalArgumentException("AI runtime profile is missing.")
            }
        )
        validate(value)
        return value
    }

    private fun validate(value: Settings) {
        require(value.language in setOf("en", "ru", "tg")) { "Unsupported language." }
        require(value.retentionDays in setOf(0, 30, 90, 365)) { "Unsupported retention period." }
        require(runCatching { AiRuntimeProfile.valueOf(value.aiRuntimeProfile) }.isSuccess) {
            "Unsupported AI runtime profile."
        }
    }
}
