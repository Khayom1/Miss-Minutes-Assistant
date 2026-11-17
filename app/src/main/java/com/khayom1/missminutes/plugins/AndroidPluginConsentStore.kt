package com.khayom1.missminutes.plugins

import android.content.Context

/** Plugin grants are per-plugin, disabled by default, and stored locally only. */
class AndroidPluginConsentStore(context: Context) : MutablePluginConsentStore {
    private val preferences = context.applicationContext.getSharedPreferences(
        "plugin_user_consent",
        Context.MODE_PRIVATE
    )

    override fun isGranted(pluginId: String): Boolean =
        isValidPluginId(pluginId) && preferences.getBoolean(pluginId, false)

    override fun setGranted(pluginId: String, granted: Boolean) {
        require(isValidPluginId(pluginId)) { "Invalid plugin id." }
        check(preferences.edit().putBoolean(pluginId, granted).commit()) {
            "Plugin consent could not be persisted."
        }
    }

    fun revokeAll() {
        check(preferences.edit().clear().commit()) {
            "Plugin consent could not be cleared."
        }
    }

    private fun isValidPluginId(pluginId: String): Boolean =
        pluginId.matches(Regex("^[a-z][a-z0-9_.-]{2,63}$"))
}
