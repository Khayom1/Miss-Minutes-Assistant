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
    }

    @Test
    fun extractsCommandsInEnglishRussianAndTajik() {
        assertEquals("open settings", WakePhraseMatcher.parse("Miss Minutes, open settings")?.command)
        assertEquals("открой настройки", WakePhraseMatcher.parse("MISS MINUTES — открой настройки")?.command)
        assertEquals("ҳолати батареяро нишон деҳ", WakePhraseMatcher.parse("Miss Minutes ҳолати батареяро нишон деҳ")?.command)
    }

    @Test
    fun rejectsWakePhraseEmbeddedAfterOtherWords() {
        assertNull(WakePhraseMatcher.parse("Hey Miss Minutes"))
        assertNull(WakePhraseMatcher.parse("Hello Miss Minutes, open settings"))
        assertNull(WakePhraseMatcher.parse("Miss Minute"))
        assertNull(WakePhraseMatcher.parse("Minutes Miss"))
        assertNull(WakePhraseMatcher.parse(""))
    }

    @Test
    fun onlyPrimaryRecognitionHypothesisCanTrigger() {
        assertEquals("open settings", WakePhraseMatcher.parseRecognitionCandidates(
            listOf("Miss Minutes, open settings", "Hey Miss Minutes")
        )?.command)
        assertNull(WakePhraseMatcher.parseRecognitionCandidates(
            listOf("Hey Miss Minutes", "Miss Minutes, open settings")
        ))
        assertFalse(WakePhraseMatcher.matchesRecognitionCandidates(emptyList()))
    }

    @Test
    fun stripsPunctuationBetweenWakePhraseAndCommand() {
        val command = "open settings"
        listOf(
            "Miss Minutes, $command",
            "Miss Minutes: $command",
            "Miss Minutes - $command",
            "Miss Minutes — $command",
            "Miss Minutes – $command",
            "Miss Minutes $command"
        ).forEach { transcript ->
            assertEquals(command, WakePhraseMatcher.parse(transcript)?.command)
        }
    }
}
