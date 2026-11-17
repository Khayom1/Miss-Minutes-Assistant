package com.khayom1.missminutes.backup

import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class SettingsBackupAdapterTest {
    private val sample = SettingsBackupAdapter.Settings("tg", true, 90, false, "MAX")

    @Test fun roundTripPreservesAllowlistedPreferences() {
        assertEquals(sample, SettingsBackupAdapter.decode(SettingsBackupAdapter.encode(sample)))
    }

    @Test fun legacyVersionOneSettingsDefaultToAutoProfile() {
        val payload = buildJsonObject {
            put("schemaVersion", 1)
            put("language", "tg")
            put("darkMode", true)
            put("retentionDays", 90)
            put("memoryContextEnabled", false)
        }
        assertEquals("AUTO", SettingsBackupAdapter.decode(payload).aiRuntimeProfile)
    }

    @Test fun rejectsCredentialLikeUnknownFields() {
        val payload = buildJsonObject {
            put("schemaVersion", 2)
            put("language", "en")
            put("darkMode", false)
            put("retentionDays", 30)
            put("memoryContextEnabled", false)
            put("aiRuntimeProfile", "AUTO")
            put("apiKey", "never-export")
        }
        assertThrows(IllegalArgumentException::class.java) { SettingsBackupAdapter.decode(payload) }
    }

    @Test fun rejectsUnknownRuntimeProfiles() {
        assertThrows(IllegalArgumentException::class.java) {
            SettingsBackupAdapter.encode(sample.copy(aiRuntimeProfile = "UNLIMITED"))
        }
    }

    @Test fun rejectsUnsupportedLanguageAndRetention() {
        assertThrows(IllegalArgumentException::class.java) {
            SettingsBackupAdapter.encode(sample.copy(language = "fr"))
        }
        assertThrows(IllegalArgumentException::class.java) {
            SettingsBackupAdapter.encode(sample.copy(retentionDays = 7))
        }
    }

    @Test fun rejectsMalformedBooleanAndSchemaVersion() {
        val payload = buildJsonObject {
            put("schemaVersion", 2)
            put("language", "en")
            put("darkMode", "yes")
            put("retentionDays", 30)
            put("memoryContextEnabled", false)
            put("aiRuntimeProfile", "AUTO")
        }
        assertThrows(IllegalArgumentException::class.java) { SettingsBackupAdapter.decode(payload) }
        val stringBoolean = buildJsonObject {
            put("schemaVersion", 2)
            put("language", "en")
            put("darkMode", "true")
            put("retentionDays", 30)
            put("memoryContextEnabled", false)
            put("aiRuntimeProfile", "AUTO")
        }
        assertThrows(IllegalArgumentException::class.java) { SettingsBackupAdapter.decode(stringBoolean) }
    }
}
