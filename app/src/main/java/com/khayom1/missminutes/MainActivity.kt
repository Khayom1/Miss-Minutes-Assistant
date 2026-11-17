package com.khayom1.missminutes

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.khayom1.missminutes.ai.AndroidSessionStore
import com.khayom1.missminutes.ai.DefaultSupabaseAuthRepository
import com.khayom1.missminutes.ai.OkHttpAiHttpTransport
import com.khayom1.missminutes.ai.SessionManager
import com.khayom1.missminutes.ai.AndroidTtsProvider
import com.khayom1.missminutes.ai.SupabaseDeepSeekProvider
import com.khayom1.missminutes.ui.MissMinutesApp

class MainActivity : ComponentActivity() {
    private var ttsProvider: AndroidTtsProvider? = null
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

        val localTts = AndroidTtsProvider(applicationContext)
        ttsProvider = localTts

        setContent {
            MissMinutesApp(
                sessionManager = sessionManager,
                aiProvider = aiProvider,
                ttsProvider = localTts
            )
        }
    }

    override fun onDestroy() {
        ttsProvider?.close()
        ttsProvider = null
        super.onDestroy()
    }
}
