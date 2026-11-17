package com.khayom1.missminutes

import android.os.Process
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.khayom1.missminutes.voice.WakeWordServiceState
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class WakeWordServiceStateTest {
    @Test
    fun enabledStateIsBoundToCurrentProcessAndClearsAfterProcessChange() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val preferences = context.getSharedPreferences("miss_minutes_voice_state", android.content.Context.MODE_PRIVATE)
        WakeWordServiceState.setEnabled(context, true)

        assertTrue(WakeWordServiceState.isEnabled(context))
        assertTrue(preferences.getInt("wake_service_pid", -1) == Process.myPid())

        preferences.edit().putInt("wake_service_pid", Process.myPid() + 1).apply()
        assertFalse(WakeWordServiceState.isEnabled(context))
        assertFalse(preferences.getBoolean("wake_word_enabled", true))
        WakeWordServiceState.setEnabled(context, false)
    }
}
