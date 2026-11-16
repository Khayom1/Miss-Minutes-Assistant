package com.khayom1.missminutes

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.khayom1.missminutes.ai.DeepSeekProxyProvider
import com.khayom1.missminutes.ai.LocalInferenceProvider
import com.khayom1.missminutes.ai.OkHttpPostTransport
import com.khayom1.missminutes.ai.ProviderRouter
import com.khayom1.missminutes.auth.InMemorySessionStore
import com.khayom1.missminutes.auth.SupabaseAuthRepository
import com.khayom1.missminutes.ui.MissMinutesApp

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val sessionStore = InMemorySessionStore()
        val transport = OkHttpPostTransport()
        val authRepository = SupabaseAuthRepository(
            supabaseUrl = BuildConfig.SUPABASE_URL,
            publishableKey = BuildConfig.SUPABASE_PUBLISHABLE_KEY,
            transport = transport,
            sessionStore = sessionStore
        )
        val remoteProvider = DeepSeekProxyProvider(
            proxyUrl = BuildConfig.BACKEND_PROXY_URL,
            supabaseUrl = BuildConfig.SUPABASE_URL,
            publishableKey = BuildConfig.SUPABASE_PUBLISHABLE_KEY,
            tokenProvider = sessionStore,
            transport = transport
        )
        val localProvider = LocalInferenceProvider(
            endpointUrl = BuildConfig.LOCAL_MODEL_URL,
            transport = transport
        )
        val providerRouter = ProviderRouter(remoteProvider, localProvider)

        setContent {
            MissMinutesApp(
                authRepository = authRepository,
                sessionStore = sessionStore,
                providerRouter = providerRouter
            )
        }
    }
}
