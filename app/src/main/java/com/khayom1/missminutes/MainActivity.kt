package com.khayom1.missminutes

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.khayom1.missminutes.ai.AndroidDeepSeekApiKeyStore
import com.khayom1.missminutes.ai.AndroidSpeechInputProvider
import com.khayom1.missminutes.ai.AndroidTtsProvider
import com.khayom1.missminutes.ai.LocalModelStore
import com.khayom1.missminutes.ai.LiteRtLmInferenceRuntime
import com.khayom1.missminutes.ai.OptionalLocalAiProvider
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
    private var localInferenceRuntime: LiteRtLmInferenceRuntime? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val transport = OkHttpAiHttpTransport()
        val apiKeyStore = AndroidDeepSeekApiKeyStore(applicationContext)
        val aiProvider = DirectDeepSeekProvider(
            keySource = DeepSeekApiKeySource { apiKeyStore.load() },
            transport = transport
        )
        val localModelStore = LocalModelStore(applicationContext)
        val localRuntime = LiteRtLmInferenceRuntime(
            modelPath = localModelStore::currentModelPath,
            cacheDirectory = cacheDir
        )
        localInferenceRuntime = localRuntime
        val localAiProvider = OptionalLocalAiProvider(localRuntime)
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
                localAiProvider = localAiProvider,
                localModelStore = localModelStore,
                validateLocalModel = localRuntime::validateModel,
                apiKeyStore = apiKeyStore,
                ttsProvider = offlineTts,
                speechInputProvider = speechInput,
                conversationStore = localConversationStore
            )
        }
    }

    override fun onDestroy() {
        ttsProvider?.close()
        ttsProvider = null
        speechInputProvider?.close()
        speechInputProvider = null
        conversationStore?.close()
        conversationStore = null
        localInferenceRuntime?.close()
        localInferenceRuntime = null
        super.onDestroy()
    }
}
