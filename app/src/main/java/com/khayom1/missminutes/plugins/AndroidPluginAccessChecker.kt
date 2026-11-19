package com.khayom1.missminutes.plugins

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.provider.Settings
import com.khayom1.missminutes.accessibility.AccessibilityAutomationService
import com.khayom1.missminutes.device.DeviceToolAuthorization
import com.khayom1.missminutes.device.DeviceToolCapability
import com.khayom1.missminutes.device.SharedPreferencesDeviceToolAuthorization

fun interface PluginCredentialChecker {
    fun isConfigured(pluginId: String, capability: PluginCapability): Boolean
}

/**
 * Re-checks app-level device grants, Android permissions, accessibility access,
 * and connected credentials immediately before each tool execution. The UI
 * receives typed decisions and requests only the missing user-approved access.
 */
class AndroidPluginAccessChecker(
    context: Context,
    private val credentialChecker: PluginCredentialChecker = PluginCredentialChecker { _, _ -> false },
    private val deviceAuthorization: DeviceToolAuthorization = SharedPreferencesDeviceToolAuthorization(context)
) : PluginAccessChecker {
    private val appContext = context.applicationContext

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
            PluginCapability.ACCESSIBILITY_AUTOMATION -> {
                if (!AccessibilityAutomationService.isServiceEnabled(appContext)) {
                    return PluginAccessDecision.PermissionRequired(
                        id = "android.accessibility_service",
                        label = "Accessibility service",
                        explanation = "Enable Miss Minutes cross-app automation in Android Accessibility Settings.",
                        settingsAction = Settings.ACTION_ACCESSIBILITY_SETTINGS
                    )
                }
                if (!AccessibilityAutomationService.isConnected()) {
                    return PluginAccessDecision.Unavailable(
                        "Android Accessibility is enabled, but the service has not connected yet."
                    )
                }
            }
            PluginCapability.DEVICE_INFORMATION,
            PluginCapability.BATTERY_STATUS,
            PluginCapability.CALENDAR_READ,
            PluginCapability.CALENDAR_WRITE,
            PluginCapability.ALARM_SCHEDULE,
            PluginCapability.CONTACTS_READ,
            PluginCapability.LOCATION_APPROXIMATE,
            PluginCapability.LOCATION_PRECISE -> Unit
        }

        val requiredDeviceCapability = requiredDeviceCapability(action.id)
        if (requiredDeviceCapability != null && !deviceAuthorization.isGranted(requiredDeviceCapability)) {
            return PluginAccessDecision.UserAuthorizationRequired(
                authorizationId = requiredDeviceCapability.name,
                explanation = "${requiredDeviceCapability.name.replace('_', ' ').lowercase().replaceFirstChar { it.uppercase() }} must be enabled in Device tools settings."
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

    private fun requiredDeviceCapability(actionId: String): DeviceToolCapability? = when (actionId) {
        "device_info" -> DeviceToolCapability.DEVICE_INFO
        "battery_status" -> DeviceToolCapability.BATTERY_STATUS
        "current_time" -> DeviceToolCapability.CURRENT_TIME
        "calendar_summary" -> DeviceToolCapability.READ_CALENDAR
        "create_reminder" -> DeviceToolCapability.CREATE_REMINDER
        "contacts_summary" -> DeviceToolCapability.READ_CONTACTS_SUMMARY
        "set_alarm" -> DeviceToolCapability.SET_ALARM
        "open_app" -> DeviceToolCapability.OPEN_APP_BY_PACKAGE
        else -> null
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
        PluginCapability.ALARM_SCHEDULE -> listOf("com.android.alarm.permission.SET_ALARM")
        PluginCapability.CONTACTS_READ -> listOf(Manifest.permission.READ_CONTACTS)
        PluginCapability.LOCATION_APPROXIMATE -> listOf(Manifest.permission.ACCESS_COARSE_LOCATION)
        PluginCapability.LOCATION_PRECISE -> listOf(
            Manifest.permission.ACCESS_COARSE_LOCATION,
            Manifest.permission.ACCESS_FINE_LOCATION
        )
        PluginCapability.DEVICE_INFORMATION,
        PluginCapability.BATTERY_STATUS,
        PluginCapability.NOTIFICATION_READ,
        PluginCapability.ACCESSIBILITY_AUTOMATION,
        PluginCapability.GITHUB_REPOSITORY_READ,
        PluginCapability.GITHUB_REPOSITORY_WRITE -> emptyList()
    }

    private fun permissionLabel(permission: String): String = when (permission) {
        "com.android.alarm.permission.SET_ALARM" -> "Alarm scheduling"
        Manifest.permission.READ_CALENDAR, Manifest.permission.WRITE_CALENDAR -> "Calendar access"
        Manifest.permission.READ_CONTACTS -> "Contacts access"
        Manifest.permission.ACCESS_COARSE_LOCATION,
        Manifest.permission.ACCESS_FINE_LOCATION -> "Location access"
        else -> "Android permission"
    }
}
