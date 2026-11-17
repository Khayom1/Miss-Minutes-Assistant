package com.khayom1.missminutes.ai

import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TtsVoicePolicyTest {
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
    fun refusesNetworkOnlyOrWrongLanguageVoices() {
        val candidates = listOf(
            TtsVoiceCandidate("en-network", "en-US", 500, true),
            TtsVoiceCandidate("ru-offline", "ru-RU", 500, false)
        )

        assertNull(selectBestOfflineTtsVoice(candidates, Locale.US))
    }
}
