package com.khayom1.missminutes.ai

import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SpeechInputSessionTest {
    @Test
    fun finishAcceptsOnlyTheActiveSession() {
        val session = SpeechInputSession()
        val id = session.begin()

        assertTrue(session.finish(id))
        assertFalse(session.finish(id))
    }

    @Test
    fun cancelInvalidatesPendingCallbacks() {
        val session = SpeechInputSession()
        val id = session.begin()

        session.cancel()

        assertFalse(session.finish(id))
    }

    @Test
    fun startingANewSessionInvalidatesThePreviousOne() {
        val session = SpeechInputSession()
        val oldId = session.begin()
        val newId = session.begin()

        assertFalse(session.finish(oldId))
        assertTrue(session.finish(newId))
    }

    @Test
    fun finishingAnOldSessionCannotFinishTheCurrentSession() {
        val session = SpeechInputSession()
        val oldId = session.begin()
        session.finish(oldId)
        val currentId = session.begin()

        assertFalse(session.finish(oldId))
        assertTrue(session.finish(currentId))
    }

    @Test
    fun repeatedCancellationIsSafe() {
        val session = SpeechInputSession()
        val id = session.begin()

        session.cancel()
        session.cancel()

        assertFalse(session.finish(id))
    }

    @Test
    fun sessionIdentifiersIncreaseAcrossCancellation() {
        val session = SpeechInputSession()
        val first = session.begin()
        session.cancel()
        val second = session.begin()

        assertTrue(second > first)
        assertTrue(session.finish(second))
    }

    @Test
    fun concurrentCallbacksCanCompleteOnlyOnce() {
        val session = SpeechInputSession()
        val id = session.begin()
        val gate = CountDownLatch(1)
        val executor = Executors.newFixedThreadPool(2)
        try {
            val callbacks = (1..2).map {
                executor.submit<Boolean> {
                    gate.await(5, TimeUnit.SECONDS)
                    session.finish(id)
                }
            }
            gate.countDown()
            val completed = callbacks.count { it.get(5, TimeUnit.SECONDS) }
            assertEquals(1, completed)
        } finally {
            executor.shutdownNow()
        }
    }
}
