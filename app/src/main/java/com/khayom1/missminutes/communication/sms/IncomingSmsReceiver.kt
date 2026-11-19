package com.khayom1.missminutes.communication.sms

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.provider.Telephony
import com.khayom1.missminutes.assistant.AndroidAssistantRole
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.RejectedExecutionException
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit

/** Processes new incoming SMS only while Assistant role and RECEIVE_SMS are granted. */
class IncomingSmsReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        if (intent?.action != Telephony.Sms.Intents.SMS_RECEIVED_ACTION) return
        if (!SmsAccessPolicy.mayProcessIncomingSms(
                assistantRoleHeld = AndroidAssistantRole.isHeld(context),
                receiveSmsGranted = context.checkSelfPermission(Manifest.permission.RECEIVE_SMS) == PackageManager.PERMISSION_GRANTED
            )
        ) return

        val messages = runCatching { Telephony.Sms.Intents.getMessagesFromIntent(intent).orEmpty().toList() }
            .getOrElse { return }
        if (messages.isEmpty()) return
        val pending = goAsync()
        try {
            EXECUTOR.execute {
                try {
                    val body = messages.joinToString("") { it.messageBody.orEmpty() }
                    val sender = messages.firstOrNull()?.originatingAddress.orEmpty()
                    val timestamp = messages.maxOfOrNull { it.timestampMillis } ?: System.currentTimeMillis()
                    SmsTriageProcessor.process(context.applicationContext, sender, body, timestamp, true)
                } catch (_: SecurityException) {
                    // User or system may revoke access while the broadcast is queued.
                } catch (_: RuntimeException) {
                    // Invalid PDU/provider failures must not crash the receiver.
                } finally {
                    pending.finish()
                }
            }
        } catch (_: RejectedExecutionException) {
            // A bounded queue prevents an SMS burst from retaining unbounded message bodies.
            pending.finish()
        }
    }

    private companion object {
        val EXECUTOR = SmsProcessingQueue.create()
    }
}

/** Bounded asynchronous work queue for system-delivered incoming SMS broadcasts. */
internal object SmsProcessingQueue {
    const val MAX_QUEUED_MESSAGES = 16

    fun create(): ThreadPoolExecutor = ThreadPoolExecutor(
        1,
        1,
        0L,
        TimeUnit.MILLISECONDS,
        ArrayBlockingQueue(MAX_QUEUED_MESSAGES),
        { task -> Thread(task, "MissMinutes-SMS-Triage").apply { isDaemon = true } },
        ThreadPoolExecutor.AbortPolicy()
    )
}
