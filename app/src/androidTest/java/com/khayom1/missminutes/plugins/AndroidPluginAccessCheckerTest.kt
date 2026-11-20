package com.khayom1.missminutes.plugins

import android.content.Context
import android.content.pm.ApplicationInfo
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import com.khayom1.missminutes.device.DeviceToolCapability
import com.khayom1.missminutes.device.DeviceToolAuthorization
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AndroidPluginAccessCheckerTest {
    private val context: Context = ApplicationProvider.getApplicationContext()

    @Test
    fun githubCapabilityRequiresConfiguredCredential() {
        val checker = AndroidPluginAccessChecker(
            context,
            PluginCredentialChecker { _, _ -> false }
        )
        val action = PluginActionSpec(
            id = "read_repository",
            capability = PluginCapability.GITHUB_REPOSITORY_READ
        )
        assertTrue(checker.inspect("github_plugin", action) is PluginAccessDecision.CredentialRequired)
    }

    @Test
    fun missingCalendarPermissionIsReportedAtExecutionTime() {
        val checker = AndroidPluginAccessChecker(
            context,
            deviceAuthorization = DeviceToolAuthorization {
                it == DeviceToolCapability.READ_CALENDAR
            }
        )
        val action = PluginActionSpec(
            id = "calendar_summary",
            capability = PluginCapability.CALENDAR_READ
        )
        assertTrue(checker.inspect("calendar_plugin", action) is PluginAccessDecision.PermissionRequired)
    }

    @Test
    fun alarmSchedulingUsesItsDeclaredPermissionInsteadOfCalendarRuntimeGrants() {
        val checker = AndroidPluginAccessChecker(
            context,
            deviceAuthorization = DeviceToolAuthorization {
                it == DeviceToolCapability.SET_ALARM
            }
        )
        val action = PluginActionSpec(
            id = "set_alarm",
            capability = PluginCapability.ALARM_SCHEDULE
        )

        assertEquals(PluginAccessDecision.Granted, checker.inspect("alarm_plugin", action))
    }

    @Test
    fun notificationReadingIsUnavailableWithoutARegisteredNotificationListener() {
        val checker = AndroidPluginAccessChecker(context)
        val action = PluginActionSpec(
            id = "read_notifications",
            capability = PluginCapability.NOTIFICATION_READ
        )

        assertTrue(checker.inspect("notifications_plugin", action) is PluginAccessDecision.Unavailable)
    }

    @Test
    fun locationCapabilitiesFailClosedInsteadOfRequestingUndeclaredPermissions() {
        val checker = AndroidPluginAccessChecker(context)
        listOf(
            PluginCapability.LOCATION_APPROXIMATE,
            PluginCapability.LOCATION_PRECISE
        ).forEach { capability ->
            val action = PluginActionSpec(id = "location", capability = capability)
            assertTrue(checker.inspect("location_plugin", action) is PluginAccessDecision.Unavailable)
        }
    }

    @Test
    fun appAutoBackupIsDisabledForLocalCredentialsAndConsent() {
        val appInfo = context.packageManager.getApplicationInfo(context.packageName, 0)
        assertEquals(0, appInfo.flags and ApplicationInfo.FLAG_ALLOW_BACKUP)
    }

    @Test
    fun deviceActionRequiresItsOwnExplicitUserAuthorization() {
        var granted = false
        val checker = AndroidPluginAccessChecker(
            context,
            deviceAuthorization = DeviceToolAuthorization {
                it == DeviceToolCapability.DEVICE_INFO && granted
            }
        )
        val action = PluginActionSpec(
            id = "device_info",
            capability = PluginCapability.DEVICE_INFORMATION
        )

        assertTrue(checker.inspect("device_plugin", action) is PluginAccessDecision.UserAuthorizationRequired)
        granted = true
        assertEquals(PluginAccessDecision.Granted, checker.inspect("device_plugin", action))
    }

}
