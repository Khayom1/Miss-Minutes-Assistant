package com.khayom1.missminutes

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.khayom1.missminutes.ai.AndroidSessionStore
import com.khayom1.missminutes.ai.AndroidTtsProvider
import com.khayom1.missminutes.ai.AndroidDeepSeekApiKeyStore
import com.khayom1.missminutes.ai.DeepSeekApiKeySource
import com.khayom1.missminutes.ai.DirectDeepSeekProvider
import com.khayom1.missminutes.ai.DefaultSupabaseAuthRepository
import com.khayom1.missminutes.ai.HybridTtsProvider
import com.khayom1.missminutes.ai.OkHttpAiHttpTransport
import com.khayom1.missminutes.ai.SessionManager
import com.khayom1.missminutes.ai.TtsProvider
import com.khayom1.missminutes.ui.MissMinutesApp

class MainActivity : ComponentActivity() {
    private var ttsProvider: TtsProvider? = null

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
        val apiKeyStore = AndroidDeepSeekApiKeyStore(applicationContext)
        val aiProvider = DirectDeepSeekProvider(
            keySource = DeepSeekApiKeySource { apiKeyStore.load() },
            transport = transport
        )

        val offlineTts = AndroidTtsProvider(applicationContext)
        val hybridTts = HybridTtsProvider(
            context = applicationContext,
            functionUrl = BuildConfig.SUPABASE_URL.trimEnd('/') + "/functions/v1/miss-minutes-tts",
            publishableKey = BuildConfig.SUPABASE_PUBLISHABLE_KEY,
            accessTokenSource = sessionManager,
            offlineFallback = offlineTts
        )
        ttsProvider = hybridTts

        setContent {
            MissMinutesApp(
                sessionManager = sessionManager,
                aiProvider = aiProvider,
                apiKeyStore = apiKeyStore,
                ttsProvider = hybridTts
            )
        }
    }

    override fun onDestroy() {
        ttsProvider?.close()
        ttsProvider = null
        super.onDestroy()
    }
}
