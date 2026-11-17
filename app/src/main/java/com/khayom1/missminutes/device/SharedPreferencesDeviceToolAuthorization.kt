package com.khayom1.missminutes.device

import android.content.Context

/** User-controlled per-capability grants. Missing values are always denied. */
class SharedPreferencesDeviceToolAuthorization(context: Context) : DeviceToolAuthorization {
    private val preferences = context.applicationContext.getSharedPreferences(
        "device_tool_permissions",
        Context.MODE_PRIVATE
    )

    override fun isGranted(capability: DeviceToolCapability): Boolean =
        preferences.getBoolean(capability.name, false)

    fun setGranted(capability: DeviceToolCapability, granted: Boolean) {
        preferences.edit().putBoolean(capability.name, granted).apply()
    }

    fun revokeAll() {
        preferences.edit().clear().apply()
    }
}
