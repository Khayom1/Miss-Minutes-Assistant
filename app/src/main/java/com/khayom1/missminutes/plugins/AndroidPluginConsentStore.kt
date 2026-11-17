package com.khayom1.missminutes.plugins

import android.content.Context

/** Plugin grants are per-plugin, disabled by default, and stored locally only. */
class AndroidPluginConsentStore(context: Context) : PluginConsentStore {
    private val preferences = context.applicationContext.getSharedPreferences(
        "plugin_user_consent",
        Context.MODE_PRIVATE
    )

    override fun isGranted(pluginId: String): Boolean =
        isValidPluginId(pluginId) && preferences.getBoolean(pluginId, false)

    fun setGranted(pluginId: String, granted: Boolean) {
        require(isValidPluginId(pluginId)) { "Invalid plugin id." }
        preferences.edit().putBoolean(pluginId, granted).apply()
    }

    fun revokeAll() {
        preferences.edit().clear().apply()
    }

    private fun isValidPluginId(pluginId: String): Boolean =
        pluginId.matches(Regex("^[a-z][a-z0-9_.-]{2,63}$"))
}
