package com.khayom1.missminutes.ai

import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.LifecycleOwner

/**
 * Stops one-shot microphone recognition when the Activity is no longer visible.
 * The UI state is reset only after the Activity returns to the foreground, not
 * while Compose is disposing its composition during the stop/destroy sequence.
 * Listening is never restarted automatically; the user must tap Dictate again.
 */
internal class SpeechInputBackgroundGuard(
    private val stopListening: () -> Unit,
    private val onForeground: () -> Unit
) : LifecycleEventObserver {
    private var stopped = false

    override fun onStateChanged(source: LifecycleOwner, event: Lifecycle.Event) {
        when (event) {
            Lifecycle.Event.ON_START -> if (stopped) {
                stopped = false
                onForeground()
            }
            Lifecycle.Event.ON_STOP -> if (!stopped) {
                stopped = true
                runCatching(stopListening)
            }
            else -> Unit
        }
    }
}
