package com.khayom1.missminutes.voice

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WakePhraseMatcherTest {
    @Test
    fun acceptsExactPhraseCaseInsensitively() {
        assertTrue(WakePhraseMatcher.matches("Miss Minutes"))
        assertTrue(WakePhraseMatcher.matches("miss minutes"))
        assertTrue(WakePhraseMatcher.matches("MISS MINUTES"))
    }

    @Test
    fun acceptsPunctuationAroundTheExactWords() {
        assertTrue(WakePhraseMatcher.matches("Miss Minutes!"))
        assertTrue(WakePhraseMatcher.matches("  Miss   Minutes... "))
    }

    @Test
    fun rejectsHeyPrefixAndEveryOtherExtraWord() {
        assertFalse(WakePhraseMatcher.matches("Hey Miss Minutes"))
        assertFalse(WakePhraseMatcher.matches("Hello Miss Minutes"))
        assertFalse(WakePhraseMatcher.matches("Miss Minutes please"))
    }

    @Test
    fun rejectsNearMatchesAndEmptyInput() {
        assertFalse(WakePhraseMatcher.matches(""))
        assertFalse(WakePhraseMatcher.matches("Miss Minute"))
        assertFalse(WakePhraseMatcher.matches("Minutes Miss"))
        assertFalse(WakePhraseMatcher.matches("Missed Minutes"))
    }
}
