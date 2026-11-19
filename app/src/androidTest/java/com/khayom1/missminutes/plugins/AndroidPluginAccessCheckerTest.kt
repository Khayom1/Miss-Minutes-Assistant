package com.khayom1.missminutes.plugins

import android.content.Context
import android.content.pm.ApplicationInfo
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
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
        val checker = AndroidPluginAccessChecker(context)
        val action = PluginActionSpec(
            id = "read_calendar",
            capability = PluginCapability.CALENDAR_READ
        )
        assertTrue(checker.inspect("calendar_plugin", action) is PluginAccessDecision.PermissionRequired)
    }

    @Test
    fun alarmSchedulingUsesItsDeclaredPermissionInsteadOfCalendarRuntimeGrants() {
        val checker = AndroidPluginAccessChecker(context)
        val action = PluginActionSpec(
            id = "set_alarm",
            capability = PluginCapability.ALARM_SCHEDULE
        )

        assertEquals(PluginAccessDecision.Granted, checker.inspect("alarm_plugin", action))
    }

    @Test
    fun appAutoBackupIsDisabledForLocalCredentialsAndConsent() {
        val appInfo = context.packageManager.getApplicationInfo(context.packageName, 0)
        assertEquals(0, appInfo.flags and ApplicationInfo.FLAG_ALLOW_BACKUP)
    }
}
