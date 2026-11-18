package com.khayom1.missminutes.assistant

import android.service.voice.VoiceInteractionSession
import android.service.voice.VoiceInteractionService

/** Lightweight system-managed entry point for the selected Miss Minutes Assistant. */
class MissMinutesVoiceInteractionService : VoiceInteractionService() {
    override fun onReady() {
        super.onReady()
        // Keep foreground app structure and screenshot context disabled by default.
        setDisabledShowContext(
            VoiceInteractionSession.SHOW_WITH_ASSIST or
                VoiceInteractionSession.SHOW_WITH_SCREENSHOT
        )
    }
}
