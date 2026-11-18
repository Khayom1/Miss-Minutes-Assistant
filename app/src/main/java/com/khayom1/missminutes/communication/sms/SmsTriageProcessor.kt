package com.khayom1.missminutes.communication.sms

import android.content.Context
import java.security.MessageDigest

data class SmsProcessResult(
    val classification: SmsClassification,
    val newlyStored: Boolean
)

/** Classifies locally and persists only redacted metadata. */
object SmsTriageProcessor {
    fun process(
        context: Context,
        senderAddress: String,
        body: String,
        receivedAtMillis: Long,
        notifyImportant: Boolean
    ): SmsProcessResult? {
        if (body.isBlank()) return null
        val classification = LocalSmsClassifier().classify(senderAddress, body)
        val timestamp = receivedAtMillis.takeIf { it > 0L } ?: System.currentTimeMillis()
        val payload = listOf(senderAddress, timestamp.toString(), body).joinToString("|")
        val fingerprint = MessageDigest.getInstance("SHA-256").digest(payload.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }
        val inserted = SmsTriageStore(context).appendIfNew(
            SmsTriageRecord(
                fingerprint = fingerprint,
                receivedAtMillis = timestamp,
                category = classification.category,
                priority = classification.priority,
                confidence = classification.confidence,
                reasons = classification.reasons
            )
        )
        if (inserted && notifyImportant && classification.needsImmediateAttention) {
            SmsTriageNotification.showImportantMessage(context, classification, fingerprint)
        }
        return SmsProcessResult(classification, inserted)
    }
}
