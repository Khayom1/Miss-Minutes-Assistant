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

class AndroidSpeechInputProvider(
    context: Context
) : SpeechInputProvider {
    private val appContext = context.applicationContext
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
        try {
            val instance = createRecognizer()
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
                    val transcript = results
                        ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                        ?.firstOrNull { it.isNotBlank() }
                        ?.trim()
                    releaseRecognizer()
                    if (transcript.isNullOrEmpty()) onError() else onResult(transcript)
                }

                override fun onError(error: Int) {
                    releaseRecognizer()
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
            releaseRecognizer()
            onError()
        } catch (_: RuntimeException) {
            releaseRecognizer()
            onError()
        }
    }

    @RequiresApi(Build.VERSION_CODES.S)
    private fun isOnDeviceAvailable(): Boolean =
        runCatching { SpeechRecognizer.isOnDeviceRecognitionAvailable(appContext) }.getOrDefault(false)

    @RequiresApi(Build.VERSION_CODES.S)
    private fun createRecognizer(): SpeechRecognizer =
        SpeechRecognizer.createOnDeviceSpeechRecognizer(appContext)

    override fun stop() {
        releaseRecognizer()
    }

    override fun close() {
        if (closed) return
        closed = true
        releaseRecognizer()
    }

    private fun releaseRecognizer() {
        recognizer?.let { current ->
            runCatching { current.cancel() }
            runCatching { current.destroy() }
        }
        recognizer = null
    }
}
