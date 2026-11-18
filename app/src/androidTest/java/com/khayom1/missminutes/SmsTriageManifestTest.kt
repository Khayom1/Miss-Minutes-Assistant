package com.khayom1.missminutes

import android.Manifest
import android.content.ComponentName
import android.content.Intent
import android.content.pm.PackageManager
import android.provider.Telephony
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.khayom1.missminutes.communication.sms.IncomingSmsReceiver
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SmsTriageManifestTest {
    @Test
    fun incomingSmsReceiverIsProtectedAndSmsPermissionsAreDeclared() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val packageManager = context.packageManager
        val receiver = packageManager.getReceiverInfo(
            ComponentName(context, IncomingSmsReceiver::class.java),
            PackageManager.GET_META_DATA
        )
        assertTrue(receiver.exported)
        assertEquals(Manifest.permission.BROADCAST_SMS, receiver.permission)

        val registrations = packageManager.queryBroadcastReceivers(
            Intent(Telephony.Sms.Intents.SMS_RECEIVED_ACTION),
            PackageManager.MATCH_DEFAULT_ONLY
        )
        assertTrue(registrations.any { it.activityInfo?.name == IncomingSmsReceiver::class.java.name })

        val packageInfo = packageManager.getPackageInfo(
            context.packageName,
            PackageManager.GET_PERMISSIONS
        )
        val permissions = packageInfo.requestedPermissions.orEmpty().toSet()
        assertTrue(permissions.contains(Manifest.permission.READ_SMS))
        assertTrue(permissions.contains(Manifest.permission.RECEIVE_SMS))
    }
}
