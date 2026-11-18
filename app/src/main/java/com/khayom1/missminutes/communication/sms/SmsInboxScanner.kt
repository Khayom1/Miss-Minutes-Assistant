package com.khayom1.missminutes.communication.sms

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.provider.Telephony
import com.khayom1.missminutes.assistant.AndroidAssistantRole

data class SmsInboxScanResult(
    val scannedMessages: Int,
    val importantMessages: Int,
    val newlyClassified: Int
)

/** Explicit, bounded scan of existing inbox messages. SMS text is never persisted or uploaded. */
object SmsInboxScanner {
    fun scanRecent(context: Context, maximumMessages: Int = 200): SmsInboxScanResult {
        require(maximumMessages in 1..500)
        val appContext = context.applicationContext
        val readGranted = appContext.checkSelfPermission(Manifest.permission.READ_SMS) == PackageManager.PERMISSION_GRANTED
        check(SmsAccessPolicy.mayReadInbox(AndroidAssistantRole.isHeld(appContext), readGranted)) {
            "The Android Assistant role and READ_SMS permission are required."
        }

        var scanned = 0
        var important = 0
        var newlyClassified = 0
        val projection = arrayOf(Telephony.Sms._ID, Telephony.Sms.ADDRESS, Telephony.Sms.BODY, Telephony.Sms.DATE)
        appContext.contentResolver.query(
            Telephony.Sms.Inbox.CONTENT_URI,
            projection,
            null,
            null,
            Telephony.Sms.DATE + " DESC"
        )?.use { cursor ->
            val addressIndex = cursor.getColumnIndexOrThrow(Telephony.Sms.ADDRESS)
            val bodyIndex = cursor.getColumnIndexOrThrow(Telephony.Sms.BODY)
            val dateIndex = cursor.getColumnIndexOrThrow(Telephony.Sms.DATE)
            while (scanned < maximumMessages && cursor.moveToNext()) {
                scanned++
                val result = SmsTriageProcessor.process(
                    appContext,
                    cursor.getString(addressIndex).orEmpty(),
                    cursor.getString(bodyIndex).orEmpty(),
                    cursor.getLong(dateIndex),
                    notifyImportant = false
                ) ?: continue
                if (result.classification.needsImmediateAttention) important++
                if (result.newlyStored) newlyClassified++
            }
        }
        return SmsInboxScanResult(scanned, important, newlyClassified)
    }
}
