package com.khayom1.missminutes.plugins

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.provider.Settings
import com.khayom1.missminutes.device.DeviceToolCapability
import com.khayom1.missminutes.device.SharedPreferencesDeviceToolAuthorization

fun interface PluginCredentialChecker {
    fun isConfigured(pluginId: String, capability: PluginCapability): Boolean
}

/**
 * Re-checks Android grants and connected credentials immediately before each
 * tool execution. This does not request permissions itself; the UI receives a
 * typed PermissionRequired result and opens the correct Android permission flow.
 */
class AndroidPluginAccessChecker(
    context: Context,
    private val credentialChecker: PluginCredentialChecker = PluginCredentialChecker { _, _ -> false }
) : PluginAccessChecker {
    private val appContext = context.applicationContext
    private val deviceAuthorization = SharedPreferencesDeviceToolAuthorization(appContext)

    override fun inspect(
        pluginId: String,
        action: PluginActionSpec
    ): PluginAccessDecision {
        when (action.capability) {
            PluginCapability.GITHUB_REPOSITORY_READ,
            PluginCapability.GITHUB_REPOSITORY_WRITE -> {
                if (!credentialChecker.isConfigured(pluginId, action.capability)) {
                    return PluginAccessDecision.CredentialRequired(
                        provider = "GitHub",
                        explanation = "Connect GitHub and grant access to the selected repository once."
                    )
                }
            }
            PluginCapability.NOTIFICATION_READ -> {
                if (!hasNotificationAccess()) {
                    return PluginAccessDecision.PermissionRequired(
                        id = "android.notification_access",
                        label = "Notification access",
                        explanation = "Miss Minutes needs Notification Access to read notifications you choose to share.",
                        settingsAction = Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS
                    )
                }
            }
            PluginCapability.DEVICE_INFORMATION,
            PluginCapability.BATTERY_STATUS,
            PluginCapability.ANDROID_SETTINGS_OPEN,
            PluginCapability.ANDROID_TEXT_SHARE,
            PluginCapability.CALENDAR_READ,
            PluginCapability.CALENDAR_WRITE,
            PluginCapability.CONTACTS_READ,
            PluginCapability.LOCATION_APPROXIMATE,
            PluginCapability.LOCATION_PRECISE -> Unit
        }

        val deviceCapability = when (action.id) {
            "open_wifi_settings" -> DeviceToolCapability.OPEN_WIFI_SETTINGS
            "open_app_settings" -> DeviceToolCapability.OPEN_APP_SETTINGS
            "share_text" -> DeviceToolCapability.SHARE_TEXT
            else -> null
        }
        if (deviceCapability != null && !deviceAuthorization.isGranted(deviceCapability)) {
            val label = when (deviceCapability) {
                DeviceToolCapability.OPEN_WIFI_SETTINGS -> "Open Wi-Fi settings"
                DeviceToolCapability.OPEN_APP_SETTINGS -> "Open app settings"
                DeviceToolCapability.SHARE_TEXT -> "Share text"
                else -> "Android device action"
            }
            return PluginAccessDecision.PermissionRequired(
                id = "device_tool.\${deviceCapability.name.lowercase()}",
                label = label,
                explanation = "Enable '\$label' under Settings → Android tools before using this plugin action."
            )
        }

        val missingPermission = requiredPermissions(action.capability).firstOrNull { permission ->
            appContext.checkSelfPermission(permission) != PackageManager.PERMISSION_GRANTED
        } ?: return PluginAccessDecision.Granted

        return PluginAccessDecision.PermissionRequired(
            id = missingPermission,
            label = permissionLabel(missingPermission),
            explanation = "${permissionLabel(missingPermission)} is required for this action.",
            androidPermission = missingPermission
        )
    }

    private fun hasNotificationAccess(): Boolean {
        val enabled = Settings.Secure.getString(
            appContext.contentResolver,
            "enabled_notification_listeners"
        ).orEmpty()
        val componentPrefix = appContext.packageName + "/"
        return enabled.split(':').any { it.startsWith(componentPrefix) }
    }

    private fun requiredPermissions(capability: PluginCapability): List<String> = when (capability) {
        PluginCapability.CALENDAR_READ -> listOf(Manifest.permission.READ_CALENDAR)
        PluginCapability.CALENDAR_WRITE -> listOf(
            Manifest.permission.READ_CALENDAR,
            Manifest.permission.WRITE_CALENDAR
        )
        PluginCapability.CONTACTS_READ -> listOf(Manifest.permission.READ_CONTACTS)
        PluginCapability.LOCATION_APPROXIMATE -> listOf(Manifest.permission.ACCESS_COARSE_LOCATION)
        PluginCapability.LOCATION_PRECISE -> listOf(
            Manifest.permission.ACCESS_COARSE_LOCATION,
            Manifest.permission.ACCESS_FINE_LOCATION
        )
        PluginCapability.DEVICE_INFORMATION,
        PluginCapability.BATTERY_STATUS,
        PluginCapability.ANDROID_SETTINGS_OPEN,
        PluginCapability.ANDROID_TEXT_SHARE,
        PluginCapability.NOTIFICATION_READ,
        PluginCapability.GITHUB_REPOSITORY_READ,
        PluginCapability.GITHUB_REPOSITORY_WRITE -> emptyList()
    }

    private fun permissionLabel(permission: String): String = when (permission) {
        Manifest.permission.READ_CALENDAR, Manifest.permission.WRITE_CALENDAR -> "Calendar access"
        Manifest.permission.READ_CONTACTS -> "Contacts access"
        Manifest.permission.ACCESS_COARSE_LOCATION,
        Manifest.permission.ACCESS_FINE_LOCATION -> "Location access"
        else -> "Android permission"
    }
}
