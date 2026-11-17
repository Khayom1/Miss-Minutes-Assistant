package com.khayom1.missminutes.overlay

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.khayom1.missminutes.overlay.MissMinutesOverlayService
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class OverlayServiceManifestTest {
    private val context: Context = ApplicationProvider.getApplicationContext()

    @Suppress("DEPRECATION")
    @Test
    fun overlayServiceIsPrivateAndDeclaresSpecialUseForegroundType() {
        val service = context.packageManager.getServiceInfo(
            ComponentName(context, MissMinutesOverlayService::class.java),
            0
        )
        assertFalse(service.exported)
        assertTrue(
            service.foregroundServiceType and ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE != 0
        )
        assertEquals(
            PackageManager.PERMISSION_GRANTED,
            context.packageManager.checkPermission(
                "android.permission.FOREGROUND_SERVICE_SPECIAL_USE",
                context.packageName
            )
        )
    }

    @Test
    fun overlayServiceExposesOnlyExplicitStartAndStopActions() {
        assertEquals(
            "com.khayom1.missminutes.overlay.START",
            MissMinutesOverlayService.ACTION_START
        )
        assertEquals(
            "com.khayom1.missminutes.overlay.STOP",
            MissMinutesOverlayService.ACTION_STOP
        )
    }
}
