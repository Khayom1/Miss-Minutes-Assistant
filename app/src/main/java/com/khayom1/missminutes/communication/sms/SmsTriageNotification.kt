package com.khayom1.missminutes.communication.sms

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.khayom1.missminutes.MainActivity
import com.khayom1.missminutes.R

object SmsTriageNotification {
    private const val CHANNEL_ID = "miss_minutes_important_sms"

    fun showImportantMessage(context: Context, classification: SmsClassification, fingerprint: String) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) return
        if (!NotificationManagerCompat.from(context).areNotificationsEnabled()) return

        val titleRes = when (classification.category) {
            SmsCategory.AUTHENTICATION_CODE -> R.string.sms_triage_title_verification
            SmsCategory.BANK_TRANSACTION -> R.string.sms_triage_title_bank
            SmsCategory.SECURITY_ALERT -> R.string.sms_triage_title_security
            SmsCategory.EMERGENCY -> R.string.sms_triage_title_urgent
            else -> R.string.sms_triage_title_important
        }
        runCatching {
            createChannel(context)
            val id = fingerprint.take(8).toLong(16).toInt() and 0x7fffffff
            val openAssistant = PendingIntent.getActivity(
                context, id,
                Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            val body = context.getString(R.string.sms_triage_notification_body)
            val notification = NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(android.R.drawable.ic_dialog_info)
                .setContentTitle(context.getString(titleRes))
                .setContentText(body)
                .setStyle(NotificationCompat.BigTextStyle().bigText(body))
                .setCategory(NotificationCompat.CATEGORY_MESSAGE)
                .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
                .setContentIntent(openAssistant)
                .setAutoCancel(true)
                .setOnlyAlertOnce(true)
                .build()
            NotificationManagerCompat.from(context).notify(id, notification)
        }
    }

    private fun createChannel(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        if (manager.getNotificationChannel(CHANNEL_ID) != null) return
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID,
                context.getString(R.string.sms_triage_notification_channel_name),
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = context.getString(R.string.sms_triage_notification_channel_description)
                lockscreenVisibility = Notification.VISIBILITY_PRIVATE
            }
        )
    }
}
