package com.khayom1.missminutes.ai

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import androidx.annotation.RequiresApi
import androidx.core.content.ContextCompat
import java.util.Locale

/** One-shot, user-initiated on-device speech recognition. No network fallback. */
interface SpeechInputProvider {
    fun start(locale: Locale, onResult: (String) -> Unit, onError: () -> Unit)
    fun stop()
    fun close()
}

/**
 * Rejects callbacks from a recognizer session after it has been stopped,
 * replaced, or completed. SpeechRecognizer may deliver cancellation callbacks
 * asynchronously, so destroying the recognizer alone is not a session boundary.
 */
internal class SpeechInputSession {
    private var generation = 0L
    private var activeGeneration: Long? = null

    @Synchronized
    fun begin(): Long {
        generation += 1
        activeGeneration = generation
        return generation
    }

    @Synchronized
    fun finish(sessionId: Long): Boolean {
        if (activeGeneration != sessionId) return false
        activeGeneration = null
        return true
    }

    @Synchronized
    fun cancel() {
        generation += 1
        activeGeneration = null
    }
}

class AndroidSpeechInputProvider(
    context: Context
) : SpeechInputProvider {
    private val appContext = context.applicationContext
    private val session = SpeechInputSession()
    private var recognizer: SpeechRecognizer? = null
    private var closed = false

    override fun start(locale: Locale, onResult: (String) -> Unit, onError: () -> Unit) {
        stop()
        if (closed ||
            ContextCompat.checkSelfPermission(appContext, Manifest.permission.RECORD_AUDIO) !=
            PackageManager.PERMISSION_GRANTED ||
            Build.VERSION.SDK_INT < Build.VERSION_CODES.S ||
            !isOnDeviceAvailable()
        ) {
            onError()
            return
        }

        val sessionId = session.begin()
        var created: SpeechRecognizer? = null
        try {
            val instance = createRecognizer()
            created = instance
            recognizer = instance
            instance.setRecognitionListener(object : RecognitionListener {
                override fun onReadyForSpeech(params: Bundle?) = Unit
                override fun onBeginningOfSpeech() = Unit
                override fun onRmsChanged(rmsdB: Float) = Unit
                override fun onBufferReceived(buffer: ByteArray?) = Unit
                override fun onEndOfSpeech() = Unit
                override fun onPartialResults(partialResults: Bundle?) = Unit
                override fun onEvent(eventType: Int, params: Bundle?) = Unit

                override fun onResults(results: Bundle?) {
                    if (!session.finish(sessionId)) return
                    val transcript = results
                        ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                        ?.firstOrNull { it.isNotBlank() }
                        ?.trim()
                    releaseRecognizer(instance)
                    if (transcript.isNullOrEmpty()) onError() else onResult(transcript)
                }

                override fun onError(error: Int) {
                    if (!session.finish(sessionId)) return
                    releaseRecognizer(instance)
                    onError()
                }
            })
            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, locale.toLanguageTag())
                putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, false)
                putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3)
                putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true)
            }
            instance.startListening(intent)
        } catch (_: SecurityException) {
            if (session.finish(sessionId)) {
                created?.let(::releaseRecognizer)
                onError()
            }
        } catch (_: RuntimeException) {
            if (session.finish(sessionId)) {
                created?.let(::releaseRecognizer)
                onError()
            }
        }
    }

    @RequiresApi(Build.VERSION_CODES.S)
    private fun isOnDeviceAvailable(): Boolean =
        runCatching { SpeechRecognizer.isOnDeviceRecognitionAvailable(appContext) }.getOrDefault(false)

    @RequiresApi(Build.VERSION_CODES.S)
    private fun createRecognizer(): SpeechRecognizer =
        SpeechRecognizer.createOnDeviceSpeechRecognizer(appContext)

    override fun stop() {
        session.cancel()
        releaseRecognizer()
    }

    override fun close() {
        if (closed) return
        closed = true
        stop()
    }

    private fun releaseRecognizer(expected: SpeechRecognizer? = null) {
        val current = expected ?: recognizer ?: return
        if (recognizer === current) recognizer = null
        runCatching { current.cancel() }
        runCatching { current.destroy() }
    }
}
