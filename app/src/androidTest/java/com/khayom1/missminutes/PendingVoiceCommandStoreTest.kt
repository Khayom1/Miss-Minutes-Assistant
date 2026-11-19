package com.khayom1.missminutes

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.khayom1.missminutes.voice.PendingVoiceCommandStore
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertFalse
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PendingVoiceCommandStoreTest {
    private val context
        get() = InstrumentationRegistry.getInstrumentation().targetContext

    @Before
    fun clearPendingCommand() {
        context.getSharedPreferences("miss_minutes_pending_voice", android.content.Context.MODE_PRIVATE)
            .edit().clear().commit()
    }

    @Test
    fun commandRemainsPendingUntilAcknowledged() {
        PendingVoiceCommandStore.save(context, "open settings")

        assertEquals("open settings", PendingVoiceCommandStore.peek(context))
        assertEquals("open settings", PendingVoiceCommandStore.peek(context))
        val stored = context.getSharedPreferences("miss_minutes_pending_voice", android.content.Context.MODE_PRIVATE)
            .getString("command_ciphertext", null)
        assertFalse(stored.orEmpty().contains("open settings"))

        PendingVoiceCommandStore.acknowledge(context)
        assertNull(PendingVoiceCommandStore.peek(context))
    }

    @Test
    fun blankCommandsAreNotQueued() {
        PendingVoiceCommandStore.save(context, "   ")
        assertNull(PendingVoiceCommandStore.peek(context))
    }
    @Test
    fun legacyPlaintextCommandIsDeletedInsteadOfReadAfterUpgrade() {
        context.getSharedPreferences("miss_minutes_pending_voice", android.content.Context.MODE_PRIVATE)
            .edit()
            .putString("command", "private voice command")
            .putLong("created_at", System.currentTimeMillis())
            .commit()

        assertNull(PendingVoiceCommandStore.peek(context))
        assertNull(
            context.getSharedPreferences("miss_minutes_pending_voice", android.content.Context.MODE_PRIVATE)
                .getString("command", null)
        )
    }

}

