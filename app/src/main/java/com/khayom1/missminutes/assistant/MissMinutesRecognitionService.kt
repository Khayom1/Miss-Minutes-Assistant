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
 *
 * Every platform recognizer belongs to one generation-scoped session. Android may
 * deliver cancellation callbacks after a recognizer is replaced, so callbacks
 * from an old session must never reach the old client or destroy a newer delegate.
 */
class MissMinutesRecognitionService : RecognitionService() {
    private val sessions = RecognitionSession<SpeechRecognizer>()

    override fun onStartListening(recognizerIntent: Intent?, callback: Callback) {
        destroyActiveRecognizer()

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

        val delegate = try {
            SpeechRecognizer.createOnDeviceSpeechRecognizer(this)
        } catch (_: SecurityException) {
            callback.error(SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS)
            return
        } catch (_: RuntimeException) {
            callback.error(SpeechRecognizer.ERROR_CLIENT)
            return
        }

        val sessionId = sessions.begin(delegate)
        try {
            delegate.setRecognitionListener(forwardingListener(sessionId, delegate, callback))
            val request = Intent(
                recognizerIntent ?: Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
            ).apply {
                putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true)
                putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            }
            delegate.startListening(request)
        } catch (_: SecurityException) {
            failSession(sessionId, delegate, callback, SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS)
        } catch (_: RuntimeException) {
            failSession(sessionId, delegate, callback, SpeechRecognizer.ERROR_CLIENT)
        }
    }

    override fun onStopListening(callback: Callback) {
        val recognizer = sessions.current()
        if (recognizer == null) {
            callback.error(SpeechRecognizer.ERROR_CLIENT)
            return
        }
        runCatching { recognizer.stopListening() }
            .onFailure {
                callback.error(SpeechRecognizer.ERROR_CLIENT)
            }
    }

    override fun onCancel(callback: Callback) {
        destroyActiveRecognizer()
    }

    override fun onDestroy() {
        destroyActiveRecognizer()
        super.onDestroy()
    }

    private fun forwardingListener(
        sessionId: Long,
        delegate: SpeechRecognizer,
        callback: Callback
    ) = object : RecognitionListener {
        override fun onReadyForSpeech(params: Bundle?) {
            deliverIfCurrent(sessionId, delegate) {
                callback.readyForSpeech(params ?: Bundle())
            }
        }

        override fun onBeginningOfSpeech() {
            deliverIfCurrent(sessionId, delegate) { callback.beginningOfSpeech() }
        }

        override fun onRmsChanged(rmsdB: Float) {
            deliverIfCurrent(sessionId, delegate) { callback.rmsChanged(rmsdB) }
        }

        override fun onBufferReceived(buffer: ByteArray?) {
            if (buffer != null) {
                deliverIfCurrent(sessionId, delegate) { callback.bufferReceived(buffer) }
            }
        }

        override fun onEndOfSpeech() {
            deliverIfCurrent(sessionId, delegate) { callback.endOfSpeech() }
        }

        override fun onError(error: Int) {
            finishSession(sessionId, delegate) { callback.error(error) }
        }

        override fun onResults(results: Bundle?) {
            finishSession(sessionId, delegate) {
                if (results == null) callback.error(SpeechRecognizer.ERROR_NO_MATCH)
                else callback.results(results)
            }
        }

        override fun onPartialResults(partialResults: Bundle?) {
            if (partialResults != null) {
                deliverIfCurrent(sessionId, delegate) { callback.partialResults(partialResults) }
            }
        }

        override fun onEvent(eventType: Int, params: Bundle?) = Unit
    }

    private inline fun deliverIfCurrent(
        sessionId: Long,
        delegate: SpeechRecognizer,
        deliver: () -> Unit
    ) {
        if (sessions.isCurrent(sessionId, delegate)) runCatching(deliver)
    }

    private inline fun finishSession(
        sessionId: Long,
        delegate: SpeechRecognizer,
        deliver: () -> Unit
    ) {
        if (!sessions.finish(sessionId, delegate)) return
        destroyRecognizer(delegate)
        runCatching(deliver)
    }

    private fun failSession(
        sessionId: Long,
        delegate: SpeechRecognizer,
        callback: Callback,
        error: Int
    ) {
        finishSession(sessionId, delegate) { callback.error(error) }
    }

    private fun destroyActiveRecognizer() {
        sessions.cancel()?.let(::destroyRecognizer)
    }

    /** Destroys only the exact delegate owned by the completed/cancelled session. */
    private fun destroyRecognizer(recognizer: SpeechRecognizer) {
        runCatching { recognizer.cancel() }
        runCatching { recognizer.destroy() }
    }
}
