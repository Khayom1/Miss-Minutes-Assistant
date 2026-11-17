package com.khayom1.missminutes

import android.content.ComponentName
import android.content.pm.ApplicationInfo
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.os.Build
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.khayom1.missminutes.overlay.MissMinutesOverlayService
import com.khayom1.missminutes.voice.MissMinutesWakeWordService
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AndroidPlatformPolicyTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val packageManager = context.packageManager

    @Test
    fun packageTargetsAndroid17AndPreservesSupportedMinimum() {
        val appInfo = packageManager.getApplicationInfo(context.packageName, 0)

        assertEquals(37, appInfo.targetSdkVersion)
        assertEquals(26, appInfo.minSdkVersion)
        assertFalse(appInfo.flags and ApplicationInfo.FLAG_ALLOW_BACKUP != 0)
        assertFalse(appInfo.flags and ApplicationInfo.FLAG_USES_CLEARTEXT_TRAFFIC != 0)
    }

    @Test
    fun appUsesInternetButDoesNotRequestLanAccess() {
        val permissions = packageInfo().requestedPermissions.orEmpty().toSet()

        assertTrue("android.permission.INTERNET" in permissions)
        assertTrue("android.permission.RECORD_AUDIO" in permissions)
        assertTrue("android.permission.POST_NOTIFICATIONS" in permissions)
        assertTrue("android.permission.FOREGROUND_SERVICE_MICROPHONE" in permissions)
        assertTrue("android.permission.FOREGROUND_SERVICE_SPECIAL_USE" in permissions)
        assertFalse("android.permission.ACCESS_LOCAL_NETWORK" in permissions)
    }

    @Test
    fun microphoneAndOverlayServicesArePrivateAndUseDeclaredForegroundTypes() {
        val microphone = packageManager.getServiceInfo(
            ComponentName(context, MissMinutesWakeWordService::class.java),
            PackageManager.GET_META_DATA
        )
        val overlay = packageManager.getServiceInfo(
            ComponentName(context, MissMinutesOverlayService::class.java),
            PackageManager.GET_META_DATA
        )

        assertFalse(microphone.exported)
        assertTrue(
            microphone.foregroundServiceType and
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE != 0
        )
        assertFalse(overlay.exported)
        assertTrue(
            overlay.foregroundServiceType and
                ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE != 0
        )
    }

    @Suppress("DEPRECATION")
    private fun packageInfo(): PackageInfo =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            packageManager.getPackageInfo(
                context.packageName,
                PackageManager.PackageInfoFlags.of(0)
            )
        } else {
            packageManager.getPackageInfo(context.packageName, 0)
        }
}
