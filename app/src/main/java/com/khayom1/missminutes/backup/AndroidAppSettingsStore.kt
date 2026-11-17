package com.khayom1.missminutes.backup

import android.content.Context

/** Persists only non-secret presentation preferences. */
class AndroidAppSettingsStore(context: Context) {
    private val preferences = context.applicationContext.getSharedPreferences(
        PREFERENCES_NAME,
        Context.MODE_PRIVATE
    )

    data class Appearance(val language: String, val darkMode: Boolean)

    fun loadAppearance(): Appearance {
        val language = preferences.getString(KEY_LANGUAGE, "English")
            ?.takeIf { it in ALLOWED_LANGUAGES } ?: "English"
        return Appearance(language, preferences.getBoolean(KEY_DARK_MODE, false))
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
        check(preferences.edit()
            .putString(KEY_LANGUAGE, settings.language)
            .putBoolean(KEY_DARK_MODE, settings.darkMode)
            .commit()) { "Backup preferences could not be restored." }
    }

    companion object {
        private const val PREFERENCES_NAME = "app_preferences"
        private const val KEY_LANGUAGE = "language"
        private const val KEY_DARK_MODE = "dark_mode"
        private val ALLOWED_LANGUAGES = setOf("English", "Russian", "Tajik")
    }
}
