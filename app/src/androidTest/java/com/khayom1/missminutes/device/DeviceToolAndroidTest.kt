package com.khayom1.missminutes.device

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DeviceToolAndroidTest {
    private val context: Context = ApplicationProvider.getApplicationContext()

    @Test
    fun capabilitiesAreDeniedByDefaultAndPersistIndependently() {
        val authorization = SharedPreferencesDeviceToolAuthorization(context)
        authorization.revokeAll()

        DeviceToolCapability.entries.forEach { capability ->
            assertFalse(authorization.isGranted(capability))
        }

        authorization.setGranted(DeviceToolCapability.BATTERY_STATUS, true)
        assertTrue(authorization.isGranted(DeviceToolCapability.BATTERY_STATUS))
        assertFalse(authorization.isGranted(DeviceToolCapability.DEVICE_INFO))
        authorization.setGranted(DeviceToolCapability.BATTERY_STATUS, false)
        assertFalse(authorization.isGranted(DeviceToolCapability.BATTERY_STATUS))
    }

    @Test
    fun auditLogNeverExceedsConfiguredSizeCap() {
        val logFile = File(context.filesDir, "device-tool-audit.log")
        logFile.delete()
        val log = FileDeviceToolAuditLog(context)

        repeat(5_000) { index ->
            log.append(
                DeviceToolAuditEntry(
                    timestampMillis = index.toLong(),
                    capability = DeviceToolCapability.DEVICE_INFO,
                    outcome = "success"
                )
            )
        }

        assertTrue(logFile.length() <= 64 * 1024L)
        logFile.delete()
    }

    @Test
    fun auditLogContainsMetadataButNeverCommandPayload() {
        val logFile = File(context.filesDir, "device-tool-audit.log")
        logFile.delete()
        val log = FileDeviceToolAuditLog(context)
        val privateText = "private-user-content-must-not-be-written"

        log.append(
            DeviceToolAuditEntry(
                timestampMillis = 1234L,
                capability = DeviceToolCapability.SHARE_TEXT,
                outcome = "success"
            )
        )

        val contents = logFile.readText()
        assertTrue(contents.contains("SHARE_TEXT"))
        assertTrue(contents.contains("success"))
        assertFalse(contents.contains(privateText))
        logFile.delete()
    }
}
