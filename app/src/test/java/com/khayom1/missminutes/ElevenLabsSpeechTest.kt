package com.khayom1.missminutes

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ElevenLabsSpeechTest {
    @Test fun requestUsesMultilingualSpeechModelAndVoiceControls() {
        val request = ElevenLabsSpeechClient.buildRequest("Hello from Miss Minutes")
        assertEquals("Hello from Miss Minutes", request.getString("text"))
        assertEquals("eleven_multilingual_v2", request.getString("model_id"))
        val settings = request.getJSONObject("voice_settings")
        assertTrue(settings.getDouble("stability") in 0.0..1.0)
        assertTrue(settings.getDouble("similarity_boost") in 0.0..1.0)
    }

    @Test fun acceptsConfiguredVoiceId() {
        ElevenLabsSpeechClient.validateVoiceId("3ooIiYiDLwKz4dFFc7WM")
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsMalformedVoiceId() {
        ElevenLabsSpeechClient.validateVoiceId("not a voice id")
    }
}
