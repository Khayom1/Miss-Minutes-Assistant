package com.khayom1.missminutes.ai

import com.khayom1.missminutes.ui.AppLanguage
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TtsVoicePolicyTest {
    @Test
    fun appLanguagesMapToTheirOwnSpeechLocales() {
        assertEquals("en", AppLanguage.English.speechLocale.language)
        assertEquals("ru", AppLanguage.Russian.speechLocale.language)
        assertEquals("tg", AppLanguage.Tajik.speechLocale.language)
    }

    @Test
    fun choosesHighestQualityOfflineVoiceForRequestedLanguage() {
        val candidates = listOf(
            TtsVoiceCandidate("en-low", "en-US", 200, false),
            TtsVoiceCandidate("en-high", "en-GB", 400, false),
            TtsVoiceCandidate("en-network", "en-US", 500, true),
            TtsVoiceCandidate("ru-high", "ru-RU", 500, false)
        )

        assertEquals(
            "en-high",
            selectBestOfflineTtsVoice(candidates, Locale.US)?.name
        )
    }

    @Test
    fun missingTajikOfflineVoiceDoesNotFallBackToEnglishOrRussian() {
        val candidates = listOf(
            TtsVoiceCandidate("en-offline", "en-US", 400, false),
            TtsVoiceCandidate("ru-offline", "ru-RU", 400, false)
        )

        assertNull(selectBestOfflineTtsVoice(candidates, AppLanguage.Tajik.speechLocale))
    }

    @Test
    fun refusesNetworkOnlyOrWrongLanguageVoices() {
        val candidates = listOf(
            TtsVoiceCandidate("en-network", "en-US", 500, true),
            TtsVoiceCandidate("ru-offline", "ru-RU", 500, false)
        )

        assertNull(selectBestOfflineTtsVoice(candidates, Locale.US))
    }
}
