package com.khayom1.missminutes.assistant

import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class RecognitionSessionTest {
    @Test
    fun onlyTheCurrentSessionCanDeliverCallbacks() {
        val sessions = RecognitionSession<Any>()
        val oldRecognizer = Any()
        val currentRecognizer = Any()

        val oldId = sessions.begin(oldRecognizer)
        val currentId = sessions.begin(currentRecognizer)

        assertFalse(sessions.isCurrent(oldId, oldRecognizer))
        assertTrue(sessions.isCurrent(currentId, currentRecognizer))
    }

    @Test
    fun finishingAnOldSessionCannotReleaseTheCurrentRecognizer() {
        val sessions = RecognitionSession<Any>()
        val oldRecognizer = Any()
        val currentRecognizer = Any()

        val oldId = sessions.begin(oldRecognizer)
        val currentId = sessions.begin(currentRecognizer)

        assertFalse(sessions.finish(oldId, oldRecognizer))
        assertSame(currentRecognizer, sessions.current())
        assertTrue(sessions.finish(currentId, currentRecognizer))
        assertNull(sessions.current())
    }

    @Test
    fun recognizerIdentityMustMatchSessionId() {
        val sessions = RecognitionSession<Any>()
        val recognizer = Any()
        val differentRecognizer = Any()
        val id = sessions.begin(recognizer)

        assertFalse(sessions.isCurrent(id, differentRecognizer))
        assertFalse(sessions.finish(id, differentRecognizer))
        assertSame(recognizer, sessions.current())
    }

    @Test
    fun terminalCallbackCanFinishOnlyOnce() {
        val sessions = RecognitionSession<Any>()
        val recognizer = Any()
        val id = sessions.begin(recognizer)

        assertTrue(sessions.finish(id, recognizer))
        assertFalse(sessions.finish(id, recognizer))
    }

    @Test
    fun cancellationReturnsAndClearsOnlyTheActiveRecognizer() {
        val sessions = RecognitionSession<Any>()
        val oldRecognizer = Any()
        sessions.begin(oldRecognizer)

        assertSame(oldRecognizer, sessions.cancel())
        assertNull(sessions.current())
        assertNull(sessions.cancel())
    }

    @Test
    fun callbackAfterCancellationCannotBecomeCurrentAgain() {
        val sessions = RecognitionSession<Any>()
        val recognizer = Any()
        val id = sessions.begin(recognizer)

        sessions.cancel()

        assertFalse(sessions.isCurrent(id, recognizer))
        assertFalse(sessions.finish(id, recognizer))
    }

    @Test
    fun sessionIdsIncreaseAcrossReplacementAndCancellation() {
        val sessions = RecognitionSession<Any>()
        val first = sessions.begin(Any())
        sessions.cancel()
        val currentRecognizer = Any()
        val second = sessions.begin(currentRecognizer)

        assertTrue(second > first)
        assertSame(currentRecognizer, sessions.current())
        assertTrue(sessions.isCurrent(second, currentRecognizer))
    }
}
