package com.khayom1.missminutes.ai

import android.content.Context
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.speech.tts.Voice
import java.util.Locale
import java.util.UUID

internal data class TtsVoiceCandidate(
    val name: String,
    val languageTag: String,
    val quality: Int,
    val requiresNetwork: Boolean
)

internal fun selectBestOfflineTtsVoice(
    candidates: List<TtsVoiceCandidate>,
    preferredLocale: Locale
): TtsVoiceCandidate? = candidates
    .asSequence()
    .filter { !it.requiresNetwork }
    .filter { Locale.forLanguageTag(it.languageTag).language == preferredLocale.language }
    .maxByOrNull { it.quality }

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
            val installedVoices = tts.voices.orEmpty()
            val candidates = installedVoices.map { voice ->
                TtsVoiceCandidate(
                    name = voice.name,
                    languageTag = voice.locale.toLanguageTag(),
                    quality = voice.quality,
                    requiresNetwork = voice.isNetworkConnectionRequired
                )
            }
            // Do not silently speak in a different language when the requested
            // language has no installed offline voice.
            val selectedCandidate = selectBestOfflineTtsVoice(candidates, locale)
            val selectedVoice = selectedCandidate?.let { candidate ->
                installedVoices.firstOrNull { it.name == candidate.name }
            }
            if (selectedVoice != null &&
                tts.setLanguage(selectedVoice.locale) >= TextToSpeech.LANG_AVAILABLE
            ) {
                tts.voice = selectedVoice
                tts.setSpeechRate(0.96f)
                tts.setPitch(0.98f)
                ready = true
            } else {
                // Never silently fall back to a network-required system voice.
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
