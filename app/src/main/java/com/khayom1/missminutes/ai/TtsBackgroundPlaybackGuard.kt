package com.khayom1.missminutes.ai

import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver

/**
 * Prevents system TTS from continuing as background audio without a dedicated
 * media-playback foreground service. Playback is intentionally foreground-only.
 */
internal class TtsBackgroundPlaybackGuard(
    private val stopPlayback: () -> Unit,
    private val onStopped: () -> Unit
) : LifecycleEventObserver {
    fun onBackgrounded() {
        stopPlayback()
        onStopped()
    }

    override fun onStateChanged(source: Lifecycle, event: Lifecycle.Event) {
        if (event == Lifecycle.Event.ON_STOP) onBackgrounded()
    }
}
