package com.khayom1.missminutes

import android.content.ComponentName
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.Manifest
import android.os.Build
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.khayom1.missminutes.voice.MissMinutesWakeWordService
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class WakeWordManifestTest {
    @Test
    fun wakeServiceIsPrivateMicrophoneForegroundServiceThatSurvivesTaskRemoval() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val info = context.packageManager.getServiceInfo(
            ComponentName(context, MissMinutesWakeWordService::class.java),
            PackageManager.GET_META_DATA
        )

        assertFalse(info.exported)
        assertFalse(info.stopWithTask)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            assertEquals(ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE, info.foregroundServiceType)
        }

        val packageInfo = context.packageManager.getPackageInfo(
            context.packageName,
            PackageManager.GET_PERMISSIONS
        )
        val permissions = packageInfo.requestedPermissions.orEmpty().toSet()
        assertTrue(permissions.contains(Manifest.permission.RECORD_AUDIO))
        assertTrue(permissions.contains(Manifest.permission.FOREGROUND_SERVICE))
        assertTrue(permissions.contains(Manifest.permission.POST_NOTIFICATIONS))
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            assertTrue(permissions.contains(Manifest.permission.FOREGROUND_SERVICE_MICROPHONE))
        }
    }
}
