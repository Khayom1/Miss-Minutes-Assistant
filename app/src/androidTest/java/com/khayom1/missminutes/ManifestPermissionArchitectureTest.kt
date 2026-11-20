package com.khayom1.missminutes

import android.Manifest
import android.content.ComponentName
import android.content.pm.PackageManager
import android.os.Build
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.khayom1.missminutes.accessibility.AccessibilityAutomationService
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ManifestPermissionArchitectureTest {
    @Test
    fun android14IsTheMinimumAndTargetSdkMatchesTheConfiguredApi36Toolchain() {
        val applicationInfo = InstrumentationRegistry.getInstrumentation()
            .targetContext.applicationInfo

        assertEquals(34, applicationInfo.minSdkVersion)
        assertEquals(36, applicationInfo.targetSdkVersion)
        assertTrue(Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
    }

    @Test
    fun declaredSensitivePermissionsHaveImplementedFeatures() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val packageInfo = context.packageManager.getPackageInfo(
            context.packageName,
            PackageManager.GET_PERMISSIONS
        )
        val permissions = packageInfo.requestedPermissions.orEmpty().toSet()

        val implementedPermissions = setOf(
            Manifest.permission.RECORD_AUDIO,
            Manifest.permission.POST_NOTIFICATIONS,
            Manifest.permission.SYSTEM_ALERT_WINDOW,
            Manifest.permission.FOREGROUND_SERVICE,
            Manifest.permission.FOREGROUND_SERVICE_MICROPHONE,
            Manifest.permission.FOREGROUND_SERVICE_SPECIAL_USE,
            Manifest.permission.READ_SMS,
            Manifest.permission.RECEIVE_SMS,
            Manifest.permission.READ_CALENDAR,
            Manifest.permission.WRITE_CALENDAR,
            Manifest.permission.READ_CONTACTS,
            "com.android.alarm.permission.SET_ALARM"
        )
        assertTrue(permissions.containsAll(implementedPermissions))

        val unimplementedPermissions = setOf(
            Manifest.permission.ACCESS_COARSE_LOCATION,
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_BACKGROUND_LOCATION,
            Manifest.permission.CAMERA,
            Manifest.permission.SEND_SMS,
            Manifest.permission.READ_CALL_LOG,
            Manifest.permission.WRITE_CALL_LOG,
            Manifest.permission.SCHEDULE_EXACT_ALARM,
            Manifest.permission.USE_EXACT_ALARM,
            "android.permission.BIND_NOTIFICATION_LISTENER_SERVICE"
        )
        assertTrue(permissions.intersect(unimplementedPermissions).isEmpty())
    }

    @Test
    fun accessibilityServiceIsExportedOnlyForTheProtectedSystemBindingContract() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val info = context.packageManager.getServiceInfo(
            ComponentName(context, AccessibilityAutomationService::class.java),
            PackageManager.GET_META_DATA
        )

        assertTrue(info.exported)
        assertEquals(Manifest.permission.BIND_ACCESSIBILITY_SERVICE, info.permission)
        assertTrue((info.metaData?.getInt("android.accessibilityservice") ?: 0) != 0)
    }

    @Test
    fun appDoesNotDeclareAnUnimplementedNotificationListenerService() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val packageInfo = context.packageManager.getPackageInfo(
            context.packageName,
            PackageManager.GET_SERVICES
        )
        val notificationListenerPermission = "android.permission.BIND_NOTIFICATION_LISTENER_SERVICE"

        assertFalse(
            packageInfo.services.orEmpty().any { it.permission == notificationListenerPermission }
        )
    }
}
