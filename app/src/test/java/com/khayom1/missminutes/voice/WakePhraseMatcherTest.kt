package com.khayom1.missminutes.voice

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class WakePhraseMatcherTest {
    @Test
    fun acceptsWakePhraseAlone() {
        assertEquals(null, WakePhraseMatcher.parse("Miss Minutes")?.command)
        assertTrue(WakePhraseMatcher.matches("MISS MINUTES!"))
        assertTrue(WakePhraseMatcher.matches("  Miss   Minutes... "))
        assertTrue(WakePhraseMatcher.matches("Miss Minute"))
        assertTrue(WakePhraseMatcher.matches("hey Miss Minutes"))
    }

    @Test
    fun extractsCommandsInEnglishRussianAndTajik() {
        assertEquals("open settings", WakePhraseMatcher.parse("Miss Minutes, open settings")?.command)
        assertEquals("открой настройки", WakePhraseMatcher.parse("MISS MINUTES — открой настройки")?.command)
        assertEquals("ҳолати батареяро нишон деҳ", WakePhraseMatcher.parse("Miss Minutes ҳолати батареяро нишон деҳ")?.command)
        assertEquals("ҳолати батареяро нишон деҳ", WakePhraseMatcher.parse("Мисс Минутка ҳолати батареяро нишон деҳ")?.command)
        assertEquals("открой настройки", WakePhraseMatcher.parse("Мисс Минутс, открой настройки")?.command)
        assertEquals("create a reminder", WakePhraseMatcher.parse("hey Miss Minutes create a reminder")?.command)
    }

    @Test
    fun rejectsNonWakePhrases() {
        assertNull(WakePhraseMatcher.parse("Hello, open settings"))
        assertNull(WakePhraseMatcher.parse("Minutes Miss"))
        assertNull(WakePhraseMatcher.parse(""))
        assertNull(WakePhraseMatcher.parse("Just a random sentence"))
    }

    @Test
    fun onlyPrimaryRecognitionHypothesisCanTrigger() {
        assertEquals("open settings", WakePhraseMatcher.parseRecognitionCandidates(
            listOf("Miss Minutes, open settings", "Hey Miss Minutes")
        )?.command)
        assertEquals("open settings", WakePhraseMatcher.parseRecognitionCandidates(
            listOf("hey Miss Minutes, open settings", "something else")
        )?.command)
        assertFalse(WakePhraseMatcher.matchesRecognitionCandidates(emptyList()))
    }
}
