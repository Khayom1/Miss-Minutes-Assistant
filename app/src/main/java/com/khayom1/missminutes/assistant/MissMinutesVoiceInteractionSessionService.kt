package com.khayom1.missminutes.assistant

import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.service.voice.VoiceInteractionSession
import android.service.voice.VoiceInteractionSessionService
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import com.khayom1.missminutes.MainActivity
import com.khayom1.missminutes.R

class MissMinutesVoiceInteractionSessionService : VoiceInteractionSessionService() {
    override fun onNewSession(args: Bundle): VoiceInteractionSession =
        MissMinutesVoiceInteractionSession(this)
}

/** Lightweight native surface; Compose and AI runtime stay outside this process. */
private class MissMinutesVoiceInteractionSession(
    private val sessionContext: Context
) : VoiceInteractionSession(sessionContext) {
    override fun onCreateContentView(): View {
        val density = sessionContext.resources.displayMetrics.density
        fun dp(value: Int) = (value * density).toInt()

        val root = LinearLayout(sessionContext).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(dp(24), dp(20), dp(24), dp(20))
            setBackgroundColor(Color.WHITE)
        }
        val title = TextView(sessionContext).apply {
            text = sessionContext.getString(R.string.miss_minutes_voice_session_title)
            textSize = 22f
            setTextColor(Color.rgb(24, 32, 44))
            gravity = Gravity.CENTER
        }
        val body = TextView(sessionContext).apply {
            text = sessionContext.getString(R.string.miss_minutes_voice_session_body)
            textSize = 14f
            setTextColor(Color.rgb(74, 84, 98))
            gravity = Gravity.CENTER
            setPadding(0, dp(12), 0, dp(16))
        }
        val openButton = Button(sessionContext).apply {
            text = sessionContext.getString(R.string.miss_minutes_voice_session_open)
            setOnClickListener {
                startAssistantActivity(Intent(sessionContext, MainActivity::class.java))
                finish()
            }
        }

        root.addView(title)
        root.addView(body)
        root.addView(openButton)
        return root
    }
}
