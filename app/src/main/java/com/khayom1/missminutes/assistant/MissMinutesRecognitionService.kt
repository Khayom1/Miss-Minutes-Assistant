package com.khayom1.missminutes.assistant

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognitionService
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer

/**
 * Speech adapter for the Android Assistant role.
 * Delegates only to Android's on-device recognizer; there is no cloud fallback.
 */
class MissMinutesRecognitionService : RecognitionService() {
    private var activeRecognizer: SpeechRecognizer? = null

    override fun onStartListening(recognizerIntent: Intent?, callback: Callback) {
        releaseActiveRecognizer()

        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            callback.error(SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS)
            return
        }
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
            callback.error(SpeechRecognizer.ERROR_CLIENT)
            return
        }
        if (!SpeechRecognizer.isOnDeviceRecognitionAvailable(this)) {
            callback.error(SpeechRecognizer.ERROR_CLIENT)
            return
        }

        try {
            val delegate = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                SpeechRecognizer.createOnDeviceSpeechRecognizer(this)
            } else {
                null
            } ?: run {
                callback.error(SpeechRecognizer.ERROR_CLIENT)
                return
            }
            activeRecognizer = delegate
            delegate.setRecognitionListener(forwardingListener(callback))
            val request = Intent(
                recognizerIntent ?: Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
            ).apply {
                putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true)
                putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            }
            delegate.startListening(request)
        } catch (_: SecurityException) {
            releaseActiveRecognizer()
            callback.error(SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS)
        } catch (_: RuntimeException) {
            releaseActiveRecognizer()
            callback.error(SpeechRecognizer.ERROR_CLIENT)
        }
    }

    override fun onStopListening(callback: Callback) {
        val recognizer = activeRecognizer
        if (recognizer == null) {
            callback.error(SpeechRecognizer.ERROR_CLIENT)
            return
        }
        runCatching { recognizer.stopListening() }
            .onFailure { callback.error(SpeechRecognizer.ERROR_CLIENT) }
    }

    override fun onCancel(callback: Callback) {
        releaseActiveRecognizer()
    }

    override fun onDestroy() {
        releaseActiveRecognizer()
        super.onDestroy()
    }

    private fun forwardingListener(callback: Callback) = object : RecognitionListener {
        override fun onReadyForSpeech(params: Bundle?) {
            runCatching { callback.readyForSpeech(params ?: Bundle()) }
        }
        override fun onBeginningOfSpeech() {
            runCatching { callback.beginningOfSpeech() }
        }
        override fun onRmsChanged(rmsdB: Float) {
            runCatching { callback.rmsChanged(rmsdB) }
        }
        override fun onBufferReceived(buffer: ByteArray?) {
            if (buffer != null) runCatching { callback.bufferReceived(buffer) }
        }
        override fun onEndOfSpeech() {
            runCatching { callback.endOfSpeech() }
        }
        override fun onError(error: Int) {
            runCatching { callback.error(error) }
            releaseActiveRecognizer()
        }
        override fun onResults(results: Bundle?) {
            if (results == null) {
                runCatching { callback.error(SpeechRecognizer.ERROR_NO_MATCH) }
            } else {
                runCatching { callback.results(results) }
            }
            releaseActiveRecognizer()
        }
        override fun onPartialResults(partialResults: Bundle?) {
            if (partialResults != null) {
                runCatching { callback.partialResults(partialResults) }
            }
        }
        override fun onEvent(eventType: Int, params: Bundle?) = Unit
    }

    private fun releaseActiveRecognizer() {
        activeRecognizer?.let { recognizer ->
            runCatching { recognizer.cancel() }
            runCatching { recognizer.destroy() }
        }
        activeRecognizer = null
    }
}
