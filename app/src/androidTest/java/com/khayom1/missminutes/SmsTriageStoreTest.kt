package com.khayom1.missminutes

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.khayom1.missminutes.communication.sms.SmsCategory
import com.khayom1.missminutes.communication.sms.SmsPriority
import com.khayom1.missminutes.communication.sms.SmsReason
import com.khayom1.missminutes.communication.sms.SmsTriageRecord
import com.khayom1.missminutes.communication.sms.SmsTriageStore
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SmsTriageStoreTest {
    @Test
    fun storePersistsOnlyClassificationMetadataAndDeduplicates() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        context.deleteSharedPreferences("miss_minutes_sms_triage")
        val store = SmsTriageStore(context)
        val record = SmsTriageRecord(
            fingerprint = "deadbeef",
            receivedAtMillis = 1_800_000_000_000L,
            category = SmsCategory.AUTHENTICATION_CODE,
            priority = SmsPriority.HIGH,
            confidence = 98,
            reasons = setOf(SmsReason.VERIFICATION_LANGUAGE_AND_CODE)
        )

        assertTrue(store.appendIfNew(record))
        assertFalse(store.appendIfNew(record))
        assertEquals(1, store.recentRecords().size)
        assertEquals(SmsPriority.HIGH, store.recentRecords().single().priority)

        val persisted = context.getSharedPreferences("miss_minutes_sms_triage", 0)
            .getString("classification_records_v1", "")
        assertFalse(persisted.orEmpty().contains("481205"))
        assertFalse(persisted.orEmpty().contains("verification code"))
    }
}
