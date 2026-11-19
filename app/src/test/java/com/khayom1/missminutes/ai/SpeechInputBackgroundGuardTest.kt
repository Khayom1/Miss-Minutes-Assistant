package com.khayom1.missminutes.ai

import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import org.junit.Assert.assertEquals
import org.junit.Test

class SpeechInputBackgroundGuardTest {
    private class TestOwner : LifecycleOwner {
        override val lifecycle: Lifecycle = LifecycleRegistry(this)
    }

    @Test
    fun stopsRecognitionOnActivityStopWithoutMutatingUiDuringDisposal() {
        val owner = TestOwner()
        val events = mutableListOf<String>()
        val guard = SpeechInputBackgroundGuard(
            stopListening = { events += "stop" },
            onForeground = { events += "clear-ui" }
        )

        guard.onStateChanged(owner, Lifecycle.Event.ON_STOP)

        assertEquals(listOf("stop"), events)
    }

    @Test
    fun doesNotStopWhileActivityIsOnlyPaused() {
        val owner = TestOwner()
        val events = mutableListOf<String>()
        val guard = SpeechInputBackgroundGuard(
            stopListening = { events += "stop" },
            onForeground = { events += "clear-ui" }
        )

        guard.onStateChanged(owner, Lifecycle.Event.ON_PAUSE)

        assertEquals(emptyList<String>(), events)
    }

    @Test
    fun duplicateStopEventsDoNotRepeatCleanup() {
        val owner = TestOwner()
        val events = mutableListOf<String>()
        val guard = SpeechInputBackgroundGuard(
            stopListening = { events += "stop" },
            onForeground = { events += "clear-ui" }
        )

        guard.onStateChanged(owner, Lifecycle.Event.ON_STOP)
        guard.onStateChanged(owner, Lifecycle.Event.ON_STOP)

        assertEquals(listOf("stop"), events)
    }

    @Test
    fun returningToForegroundClearsUiAfterBackgroundStop() {
        val owner = TestOwner()
        val events = mutableListOf<String>()
        val guard = SpeechInputBackgroundGuard(
            stopListening = { events += "stop" },
            onForeground = { events += "clear-ui" }
        )

        guard.onStateChanged(owner, Lifecycle.Event.ON_STOP)
        guard.onStateChanged(owner, Lifecycle.Event.ON_START)

        assertEquals(listOf("stop", "clear-ui"), events)
    }

    @Test
    fun nextBackgroundSessionCanBeStoppedAgain() {
        val owner = TestOwner()
        val events = mutableListOf<String>()
        val guard = SpeechInputBackgroundGuard(
            stopListening = { events += "stop" },
            onForeground = { events += "clear-ui" }
        )

        guard.onStateChanged(owner, Lifecycle.Event.ON_STOP)
        guard.onStateChanged(owner, Lifecycle.Event.ON_START)
        guard.onStateChanged(owner, Lifecycle.Event.ON_STOP)

        assertEquals(listOf("stop", "clear-ui", "stop"), events)
    }

    @Test
    fun stateIsResetEvenWhenRecognizerCleanupThrows() {
        val owner = TestOwner()
        val events = mutableListOf<String>()
        val guard = SpeechInputBackgroundGuard(
            stopListening = { throw IllegalStateException("recognizer already destroyed") },
            onForeground = { events += "clear-ui" }
        )

        guard.onStateChanged(owner, Lifecycle.Event.ON_STOP)
        guard.onStateChanged(owner, Lifecycle.Event.ON_START)

        assertEquals(listOf("clear-ui"), events)
    }

    @Test
    fun startAndResumeEventsDoNotStopOrResetAnythingWithoutPriorStop() {
        val owner = TestOwner()
        val events = mutableListOf<String>()
        val guard = SpeechInputBackgroundGuard(
            stopListening = { events += "stop" },
            onForeground = { events += "clear-ui" }
        )

        guard.onStateChanged(owner, Lifecycle.Event.ON_CREATE)
        guard.onStateChanged(owner, Lifecycle.Event.ON_START)
        guard.onStateChanged(owner, Lifecycle.Event.ON_RESUME)

        assertEquals(emptyList<String>(), events)
    }
}
