package com.khayom1.missminutes.voice

/**
 * Bounded exponential retry delay for transient SpeechRecognizer failures.
 * A successful recognition result resets the sequence so normal listening
 * resumes quickly after temporary silence or recognizer contention.
 */
internal class RecognitionRetryBackoff(
    private val initialDelayMillis: Long = 500L,
    private val maxDelayMillis: Long = 8_000L
) {
    private var nextDelay = initialDelayMillis

    init {
        require(initialDelayMillis > 0L)
        require(maxDelayMillis >= initialDelayMillis)
    }

    @Synchronized
    fun nextDelayMillis(): Long {
        val delay = nextDelay
        nextDelay = if (nextDelay > maxDelayMillis / 2L) maxDelayMillis else (nextDelay * 2L).coerceAtMost(maxDelayMillis)
        return delay
    }

    @Synchronized
    fun reset() {
        nextDelay = initialDelayMillis
    }
}
