package com.khayom1.missminutes.ai

import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import org.junit.Assert.assertEquals
import org.junit.Test

class TtsBackgroundPlaybackGuardTest {
    private class TestOwner : LifecycleOwner {
        override val lifecycle: Lifecycle = LifecycleRegistry(this)
    }

    @Test
    fun stopsPlaybackAndClearsSpeakingStateOnlyWhenActivityStops() {
        val owner = TestOwner()
        val calls = mutableListOf<String>()
        val guard = TtsBackgroundPlaybackGuard(
            stopPlayback = { calls += "stop" },
            onStopped = { calls += "clear-ui" }
        )

        guard.onStateChanged(owner.lifecycle, Lifecycle.Event.ON_RESUME)
        assertEquals(emptyList<String>(), calls)

        guard.onStateChanged(owner.lifecycle, Lifecycle.Event.ON_STOP)
        assertEquals(listOf("stop", "clear-ui"), calls)
    }
}
