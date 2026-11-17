package com.khayom1.missminutes

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.khayom1.missminutes.ai.AndroidSessionStore
import com.khayom1.missminutes.ai.DefaultSupabaseAuthRepository
import com.khayom1.missminutes.ai.OkHttpAiHttpTransport
import com.khayom1.missminutes.ai.SessionManager
import com.khayom1.missminutes.ai.AndroidTtsPlayback
import com.khayom1.missminutes.ai.OkHttpTtsHttpTransport
import com.khayom1.missminutes.ai.SupabaseElevenLabsTtsProvider
import com.khayom1.missminutes.ai.SupabaseDeepSeekProvider
import com.khayom1.missminutes.ui.MissMinutesApp

class MainActivity : ComponentActivity() {
    private var ttsPlayback: AndroidTtsPlayback? = null
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val transport = OkHttpAiHttpTransport()
        val sessionManager = SessionManager(
            repository = DefaultSupabaseAuthRepository(
                projectUrl = BuildConfig.SUPABASE_URL,
                publishableKey = BuildConfig.SUPABASE_PUBLISHABLE_KEY,
                transport = transport
            ),
            store = AndroidSessionStore(applicationContext)
        )
        val aiProvider = SupabaseDeepSeekProvider(
            functionUrl = "${BuildConfig.SUPABASE_URL.trimEnd('/')}/functions/v1/miss-minutes-ai",
            publishableKey = BuildConfig.SUPABASE_PUBLISHABLE_KEY,
            accessTokenSource = sessionManager,
            transport = transport
        )

        val playback = AndroidTtsPlayback(applicationContext)
        ttsPlayback = playback
        val ttsProvider = SupabaseElevenLabsTtsProvider(
            functionUrl = "${BuildConfig.SUPABASE_URL.trimEnd('/')}/functions/v1/miss-minutes-tts",
            publishableKey = BuildConfig.SUPABASE_PUBLISHABLE_KEY,
            accessTokenSource = sessionManager,
            transport = OkHttpTtsHttpTransport()
        )

        setContent {
            MissMinutesApp(
                sessionManager = sessionManager,
                aiProvider = aiProvider,
                ttsProvider = ttsProvider,
                ttsPlayback = playback
            )
        }
    }

    override fun onDestroy() {
        ttsPlayback?.close()
        ttsPlayback = null
        super.onDestroy()
    }
}
