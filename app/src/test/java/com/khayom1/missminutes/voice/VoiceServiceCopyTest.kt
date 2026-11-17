package com.khayom1.missminutes.voice

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class VoiceServiceCopyTest {
    @Test
    fun everySupportedLanguageHasCompleteForegroundAndStatusCopy() {
        val copies = listOf("en", "ru", "tg").map(VoiceServiceCopyProvider::forLanguage)
        copies.forEach { copy ->
            listOf(
                copy.channelName,
                copy.channelDescription,
                copy.android12Required,
                copy.recognizerUnavailable,
                copy.permissionsRequired,
                copy.listeningState,
                copy.listeningTitle,
                copy.listeningText,
                copy.stopAction,
                copy.wakeHeard,
                copy.stoppedWithError,
                copy.stopped
            ).forEach { assertTrue(it.isNotBlank()) }
        }
        assertNotEquals(copies[0].stopAction, copies[1].stopAction)
        assertNotEquals(copies[1].stopAction, copies[2].stopAction)
    }

    @Test
    fun unknownLanguageFallsBackToEnglish() {
        assertEquals(
            VoiceServiceCopyProvider.forLanguage("en"),
            VoiceServiceCopyProvider.forLanguage("unknown")
        )
    }

    @Test
    fun onlyExplicitStartActionCanEnableMicrophoneService() {
        assertTrue(WakeWordServicePolicy.shouldStart(MissMinutesWakeWordService.ACTION_START))
        assertFalse(WakeWordServicePolicy.shouldStart(null))
        assertFalse(WakeWordServicePolicy.shouldStart(MissMinutesWakeWordService.ACTION_STOP))
        assertFalse(WakeWordServicePolicy.shouldStart("unknown.action"))
        assertTrue(WakeWordServicePolicy.shouldStop(MissMinutesWakeWordService.ACTION_STOP))
        assertFalse(WakeWordServicePolicy.shouldStop(MissMinutesWakeWordService.ACTION_START))
    }
}
