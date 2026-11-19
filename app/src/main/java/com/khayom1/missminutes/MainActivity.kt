package com.khayom1.missminutes

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.khayom1.missminutes.ai.AndroidDeepSeekApiKeyStore
import com.khayom1.missminutes.ai.AndroidSpeechInputProvider
import com.khayom1.missminutes.ai.AndroidTtsProvider
import com.khayom1.missminutes.ai.DeepSeekApiKeySource
import com.khayom1.missminutes.ai.DirectDeepSeekProvider
import com.khayom1.missminutes.ai.OkHttpAiHttpTransport
import com.khayom1.missminutes.ai.SpeechInputProvider
import com.khayom1.missminutes.ai.TtsProvider
import com.khayom1.missminutes.memory.AndroidKeystoreConversationCipher
import com.khayom1.missminutes.memory.LocalConversationStore
import com.khayom1.missminutes.ui.MissMinutesApp

class MainActivity : ComponentActivity() {
    private var ttsProvider: TtsProvider? = null
    private var speechInputProvider: SpeechInputProvider? = null
    private var conversationStore: LocalConversationStore? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val transport = OkHttpAiHttpTransport()
        val apiKeyStore = AndroidDeepSeekApiKeyStore(applicationContext)
        val aiProvider = DirectDeepSeekProvider(
            keySource = DeepSeekApiKeySource { apiKeyStore.load() },
            transport = transport
        )
        val offlineTts = AndroidTtsProvider(applicationContext)
        val speechInput = AndroidSpeechInputProvider(applicationContext)
        val localConversationStore = LocalConversationStore(
            context = applicationContext,
            cipher = AndroidKeystoreConversationCipher()
        )

        ttsProvider = offlineTts
        speechInputProvider = speechInput
        conversationStore = localConversationStore

        setContent {
            MissMinutesApp(
                aiProvider = aiProvider,
                apiKeyStore = apiKeyStore,
                ttsProvider = offlineTts,
                speechInputProvider = speechInput,
                conversationStore = localConversationStore
            )
        }
    }

    override fun onDestroy() {
        // Let ComponentActivity dispatch ON_DESTROY and dispose the Compose
        // composition before closing resources still used by its coroutines.
        super.onDestroy()
        ttsProvider?.close()
        ttsProvider = null
        speechInputProvider?.close()
        speechInputProvider = null
        conversationStore?.close()
        conversationStore = null
    }
}
