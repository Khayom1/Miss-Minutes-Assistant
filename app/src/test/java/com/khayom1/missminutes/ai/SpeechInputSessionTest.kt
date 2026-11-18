package com.khayom1.missminutes.ai

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
}
