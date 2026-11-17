package com.khayom1.missminutes.voice

import org.junit.Assert.assertEquals
import org.junit.Test

class RecognitionRetryBackoffTest {
    @Test
    fun transientFailuresBackOffExponentiallyAndRespectMaximum() {
        val backoff = RecognitionRetryBackoff()
        assertEquals(500L, backoff.nextDelayMillis())
        assertEquals(1_000L, backoff.nextDelayMillis())
        assertEquals(2_000L, backoff.nextDelayMillis())
        assertEquals(4_000L, backoff.nextDelayMillis())
        assertEquals(8_000L, backoff.nextDelayMillis())
        assertEquals(8_000L, backoff.nextDelayMillis())
    }

    @Test
    fun successfulRecognitionResetsRetryDelay() {
        val backoff = RecognitionRetryBackoff()
        repeat(4) { backoff.nextDelayMillis() }
        backoff.reset()
        assertEquals(500L, backoff.nextDelayMillis())
    }

    @Test
    fun largeCustomDelayCannotOverflow() {
        val backoff = RecognitionRetryBackoff(
            initialDelayMillis = Long.MAX_VALUE / 2L + 1L,
            maxDelayMillis = Long.MAX_VALUE
        )
        assertEquals(Long.MAX_VALUE / 2L + 1L, backoff.nextDelayMillis())
        assertEquals(Long.MAX_VALUE, backoff.nextDelayMillis())
    }

    @Test
    fun invalidDelayConfigurationIsRejected() {
        runCatching { RecognitionRetryBackoff(initialDelayMillis = 0L) }
            .onSuccess { throw AssertionError("Zero delay must be rejected.") }
        runCatching { RecognitionRetryBackoff(initialDelayMillis = 1_000L, maxDelayMillis = 500L) }
            .onSuccess { throw AssertionError("A maximum below the initial delay must be rejected.") }
    }
}
