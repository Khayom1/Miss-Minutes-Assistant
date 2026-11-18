package com.khayom1.missminutes.backup

import android.content.Context
import com.khayom1.missminutes.ai.AiRuntimeProfile

/** Persists only non-secret presentation and runtime preferences. */
class AndroidAppSettingsStore(context: Context) {
    private val preferences = context.applicationContext.getSharedPreferences(
        PREFERENCES_NAME,
        Context.MODE_PRIVATE
    )

    data class Appearance(val language: String, val darkMode: Boolean)

    fun loadAppearance(): Appearance {
        val language = preferences.getString(KEY_LANGUAGE, "en")
            ?.takeIf { it in ALLOWED_LANGUAGES } ?: "en"
        return Appearance(language, preferences.getBoolean(KEY_DARK_MODE, false))
    }

    fun loadAiRuntimeProfile(): AiRuntimeProfile {
        val stored = preferences.getString(KEY_AI_RUNTIME_PROFILE, AiRuntimeProfile.AUTO.name)
        return runCatching { AiRuntimeProfile.valueOf(stored ?: AiRuntimeProfile.AUTO.name) }
            .getOrDefault(AiRuntimeProfile.AUTO)
    }

    @Synchronized
    fun saveAiRuntimeProfile(profile: AiRuntimeProfile) {
        check(preferences.edit().putString(KEY_AI_RUNTIME_PROFILE, profile.name).commit()) {
            "AI runtime profile could not be saved."
        }
    }

    @Synchronized
    fun saveAppearance(language: String, darkMode: Boolean) {
        require(language in ALLOWED_LANGUAGES)
        check(preferences.edit()
            .putString(KEY_LANGUAGE, language)
            .putBoolean(KEY_DARK_MODE, darkMode)
            .commit()) { "Appearance preferences could not be saved." }
    }

    @Synchronized
    fun applyBackup(settings: SettingsBackupAdapter.Settings) {
        require(settings.language in ALLOWED_LANGUAGES)
        val runtimeProfile = runCatching { AiRuntimeProfile.valueOf(settings.aiRuntimeProfile) }
            .getOrElse { throw IllegalArgumentException("Unsupported AI runtime profile.", it) }
        check(preferences.edit()
            .putString(KEY_LANGUAGE, settings.language)
            .putBoolean(KEY_DARK_MODE, settings.darkMode)
            .putString(KEY_AI_RUNTIME_PROFILE, runtimeProfile.name)
            .commit()) { "Backup preferences could not be restored." }
    }

    companion object {
        private const val PREFERENCES_NAME = "app_preferences"
        private const val KEY_LANGUAGE = "language"
        private const val KEY_DARK_MODE = "dark_mode"
        private const val KEY_AI_RUNTIME_PROFILE = "ai_runtime_profile"
        private val ALLOWED_LANGUAGES = setOf("en", "ru", "tg")
    }
}
