package com.khayom1.missminutes

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AppLocalizationTest {
    @Test fun allThreeLanguagesHaveDistinctCoreUiStrings() {
        val keys = listOf("settings", "overlay_button", "input_hint", "provider_profile", "voice_section", "save")
        keys.forEach { key ->
            val tajik = AppLocalization.text("tg", key)
            val russian = AppLocalization.text("ru", key)
            val english = AppLocalization.text("en", key)
            assertTrue(tajik.isNotBlank())
            assertTrue(russian.isNotBlank())
            assertTrue(english.isNotBlank())
            assertNotEquals(tajik, russian)
            assertNotEquals(russian, english)
        }
    }

    @Test fun languageTagsAreNormalized() {
        assertEquals("tg-TJ", AppLocalization.languageTag("tg"))
        assertEquals("ru-RU", AppLocalization.languageTag("ru"))
        assertEquals("en-US", AppLocalization.languageTag("en"))
        assertEquals("ru", AppLocalization.normalize("unknown"))
    }

    @Test fun systemPromptUsesSelectedLanguage() {
        assertTrue(AppLocalization.systemPrompt("tg", "archive").contains("тоҷикӣ"))
        assertTrue(AppLocalization.systemPrompt("en", "archive").contains("English"))
        assertTrue(AppLocalization.systemPrompt("ru", "archive").contains("русски"))
    }
}
