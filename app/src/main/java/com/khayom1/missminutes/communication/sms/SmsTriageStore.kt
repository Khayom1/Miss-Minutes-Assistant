package com.khayom1.missminutes.communication.sms

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/** Stores classification metadata only: no SMS bodies, sender addresses, OTPs or links. */
data class SmsTriageRecord(
    val fingerprint: String,
    val receivedAtMillis: Long,
    val category: SmsCategory,
    val priority: SmsPriority,
    val confidence: Int,
    val reasons: Set<SmsReason>
)

class SmsTriageStore(context: Context) {
    private val preferences = context.applicationContext.getSharedPreferences(NAME, Context.MODE_PRIVATE)

    fun appendIfNew(record: SmsTriageRecord): Boolean = synchronized(LOCK) {
        val current = readArray()
        for (index in 0 until current.length()) {
            if (current.optJSONObject(index)?.optString("fingerprint") == record.fingerprint) {
                return@synchronized false
            }
        }
        val next = JSONArray()
        val first = (current.length() - MAX_RECORDS + 1).coerceAtLeast(0)
        for (index in first until current.length()) current.optJSONObject(index)?.let(next::put)
        next.put(record.toJson())
        preferences.edit().putString(KEY, next.toString()).commit()
    }

    fun recentRecords(): List<SmsTriageRecord> = synchronized(LOCK) {
        val rows = readArray()
        buildList {
            for (index in 0 until rows.length()) {
                val item = rows.optJSONObject(index) ?: continue
                val category = runCatching { SmsCategory.valueOf(item.optString("category")) }.getOrNull() ?: continue
                val priority = runCatching { SmsPriority.valueOf(item.optString("priority")) }.getOrNull() ?: continue
                val reasons = item.optString("reasons").split(',')
                    .mapNotNull { runCatching { SmsReason.valueOf(it) }.getOrNull() }.toSet()
                add(SmsTriageRecord(
                    fingerprint = item.optString("fingerprint"),
                    receivedAtMillis = item.optLong("receivedAtMillis"),
                    category = category,
                    priority = priority,
                    confidence = item.optInt("confidence").coerceIn(0, 100),
                    reasons = reasons
                ))
            }
        }
    }

    private fun readArray() = runCatching { JSONArray(preferences.getString(KEY, "[]")) }
        .getOrElse { JSONArray() }

    private fun SmsTriageRecord.toJson() = JSONObject().apply {
        put("fingerprint", fingerprint)
        put("receivedAtMillis", receivedAtMillis)
        put("category", category.name)
        put("priority", priority.name)
        put("confidence", confidence)
        put("reasons", reasons.joinToString(",") { it.name })
    }

    private companion object {
        const val NAME = "miss_minutes_sms_triage"
        const val KEY = "classification_records_v1"
        const val MAX_RECORDS = 250
        val LOCK = Any()
    }
}
