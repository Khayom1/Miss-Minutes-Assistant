package com.khayom1.missminutes.plugins

import android.content.Context
import com.khayom1.missminutes.accessibility.AccessibilityAutomationService

/** Cross-app automation uses the Android Accessibility grant as its persistent user authorization. */
class AndroidPluginConsentStore(context: Context) : MutablePluginConsentStore {
    private val appContext = context.applicationContext
    private val preferences = appContext.getSharedPreferences(
        "plugin_user_consent",
        Context.MODE_PRIVATE
    )

    override fun isGranted(pluginId: String): Boolean {
        if (!isValidPluginId(pluginId)) return false
        if (pluginId == AndroidAccessibilityAutomationPlugin.PLUGIN_ID) {
            return AccessibilityAutomationService.isServiceEnabled(appContext)
        }
        return preferences.getBoolean(pluginId, false)
    }

    override fun setGranted(pluginId: String, granted: Boolean) {
        require(isValidPluginId(pluginId)) { "Invalid plugin id." }
        if (pluginId == AndroidAccessibilityAutomationPlugin.PLUGIN_ID) {
            check(granted == AccessibilityAutomationService.isServiceEnabled(appContext)) {
                "Cross-app automation is controlled in Android Accessibility Settings."
            }
            return
        }
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
