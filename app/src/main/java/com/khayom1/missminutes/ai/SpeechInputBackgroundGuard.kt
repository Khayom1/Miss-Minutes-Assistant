package com.khayom1.missminutes.ai

import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.LifecycleOwner

/**
 * Stops one-shot microphone recognition when the Activity is no longer visible.
 * Returning to the foreground never restarts listening; the user must tap Dictate.
 */
internal class SpeechInputBackgroundGuard(
    private val stopListening: () -> Unit,
    private val onStopped: () -> Unit
) : LifecycleEventObserver {
    private var stopped = false

    override fun onStateChanged(source: LifecycleOwner, event: Lifecycle.Event) {
        when (event) {
            Lifecycle.Event.ON_START -> stopped = false
            Lifecycle.Event.ON_STOP -> if (!stopped) {
                stopped = true
                runCatching(stopListening)
                onStopped()
            }
            else -> Unit
        }
    }
}
