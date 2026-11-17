package com.khayom1.missminutes.ai

import android.content.Context
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.speech.tts.Voice
import java.util.Locale
import java.util.UUID

/** On-device speech synthesis. No network, API key, account session or audio upload. */
interface TtsProvider {
    fun speak(text: String, onDone: () -> Unit, onError: () -> Unit)
    fun stop()
    fun close()
}

class AndroidTtsProvider(
    context: Context,
    private val locale: Locale = Locale.getDefault()
) : TtsProvider {
    private val appContext = context.applicationContext
    @Volatile private var engine: TextToSpeech? = null
    @Volatile private var ready = false
    @Volatile private var activeUtteranceId: String? = null
    @Volatile private var completion: (() -> Unit)? = null
    @Volatile private var failure: (() -> Unit)? = null

    init {
        TextToSpeech(appContext) { status ->
            val tts = engine
            if (status != TextToSpeech.SUCCESS || tts == null) {
                ready = false
                return@TextToSpeech
            }
            val requested = tts.isLanguageAvailable(locale)
            val fallback = if (requested >= TextToSpeech.LANG_AVAILABLE) locale else Locale.getDefault()
            val result = tts.setLanguage(fallback)
            if (result >= TextToSpeech.LANG_AVAILABLE) {
                // Prefer an installed offline voice and the highest available quality.
                val voice = tts.voices
                    ?.filter { it.locale.language == fallback.language && !it.isNetworkConnectionRequired }
                    ?.maxByOrNull { it.quality }
                if (voice != null) tts.voice = voice
                tts.setSpeechRate(0.96f)
                tts.setPitch(0.98f)
                ready = true
            } else {
                ready = false
            }
        }.also { created ->
            engine = created
            created.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                override fun onStart(utteranceId: String) = Unit
                override fun onDone(utteranceId: String) {
                    if (activeUtteranceId == utteranceId) {
                        activeUtteranceId = null
                        completion?.invoke()
                        completion = null
                        failure = null
                    }
                }
                @Deprecated("Deprecated in Java")
                override fun onError(utteranceId: String) = finishWithError(utteranceId)
                override fun onError(utteranceId: String, errorCode: Int) = finishWithError(utteranceId)
            })
        }
    }

    override fun speak(text: String, onDone: () -> Unit, onError: () -> Unit) {
        val normalized = text.trim().take(8_000)
        val tts = engine
        if (normalized.isEmpty() || !ready || tts == null) {
            onError()
            return
        }
        stop()
        val id = UUID.randomUUID().toString()
        activeUtteranceId = id
        completion = onDone
        failure = onError
        val result = tts.speak(normalized, TextToSpeech.QUEUE_FLUSH, null, id)
        if (result == TextToSpeech.ERROR) finishWithError(id)
    }

    private fun finishWithError(id: String) {
        if (activeUtteranceId == id) {
            activeUtteranceId = null
            failure?.invoke()
            completion = null
            failure = null
        }
    }

    override fun stop() {
        activeUtteranceId = null
        completion = null
        failure = null
        engine?.stop()
    }

    override fun close() {
        stop()
        ready = false
        engine?.shutdown()
        engine = null
    }
}
