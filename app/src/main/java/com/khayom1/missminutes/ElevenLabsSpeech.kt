package com.khayom1.missminutes

import android.content.Context
import android.media.MediaPlayer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * Optional ElevenLabs TTS. The API key is supplied by the caller from SecretStore and is
 * never persisted by this class. If synthesis fails, callers should fall back to Android TTS.
 */
class ElevenLabsSpeechClient(context: Context) {
    private val appContext = context.applicationContext
    private var player: MediaPlayer? = null
    private var activeFile: File? = null
    private var playbackFinished: (() -> Unit)? = null

    suspend fun speak(apiKey: String, voiceId: String, text: String, onPlaybackFinished: () -> Unit = {}) {
        require(apiKey.isNotBlank()) { "ElevenLabs API key is required" }
        validateVoiceId(voiceId)
        require(text.isNotBlank() && text.length <= MAX_TEXT_CHARS) { "Speech text is empty or too long" }
        val audio = withContext(Dispatchers.IO) { synthesize(apiKey, voiceId, text) }
        withContext(Dispatchers.Main.immediate) { play(audio, onPlaybackFinished) }
    }

    fun release() {
        stopCurrentPlayback()
    }

    private fun stopCurrentPlayback(notifyCompletion: Boolean = true) {
        val active = player
        player = null
        runCatching { active?.stop() }
        runCatching { active?.release() }
        activeFile?.delete()
        activeFile = null
        val callback = playbackFinished
        playbackFinished = null
        if (notifyCompletion) callback?.invoke()
    }

    private fun finishPlayback(media: MediaPlayer, file: File) {
        if (player === media) {
            player = null
            runCatching { media.release() }
            file.delete()
            activeFile = null
            val callback = playbackFinished
            playbackFinished = null
            callback?.invoke()
        }
    }

    private fun synthesize(apiKey: String, voiceId: String, text: String): File {
        val endpoint = "https://api.elevenlabs.io/v1/text-to-speech/$voiceId?output_format=mp3_44100_128"
        val connection = URL(endpoint).openConnection() as HttpURLConnection
        connection.instanceFollowRedirects = false
        connection.requestMethod = "POST"
        connection.connectTimeout = CONNECT_TIMEOUT_MS
        connection.readTimeout = READ_TIMEOUT_MS
        connection.doOutput = true
        connection.setRequestProperty("xi-api-key", apiKey)
        connection.setRequestProperty("Content-Type", "application/json")
        connection.setRequestProperty("Accept", "audio/mpeg")
        val request = buildRequest(text)
        return try {
            connection.outputStream.use { it.write(request.toString().toByteArray(Charsets.UTF_8)) }
            val status = connection.responseCode
            if (status !in 200..299) {
                val detail = connection.errorStream?.use { it.readBoundedText(MAX_ERROR_BYTES) }.orEmpty()
                val message = runCatching {
                    val json = JSONObject(detail)
                    json.optJSONObject("detail")?.optString("message").orEmpty()
                        .ifBlank { json.optString("message") }
                        .ifBlank { json.optString("detail") }
                }.getOrNull().orEmpty().take(300)
                throw IllegalStateException("ElevenLabs HTTP $status" + if (message.isBlank()) "" else ": $message")
            }
            val bytes = connection.inputStream.use { it.readBoundedBytes(MAX_AUDIO_BYTES) }
            val hasId3Header = bytes.size >= 3 && bytes[0] == 0x49.toByte() &&
                bytes[1] == 0x44.toByte() && bytes[2] == 0x33.toByte()
            val hasMpegFrame = bytes.size >= 2 && bytes[0] == 0xFF.toByte() &&
                (bytes[1].toInt() and 0xE0) == 0xE0
            require(hasId3Header || hasMpegFrame) { "ElevenLabs returned an invalid MP3 payload" }
            val file = File.createTempFile("miss-minutes-", ".mp3", appContext.cacheDir)
            file.writeBytes(bytes)
            file
        } finally {
            connection.disconnect()
        }
    }

    private suspend fun play(file: File, onPlaybackFinished: () -> Unit) = suspendCancellableCoroutine<Unit> { continuation ->
        stopCurrentPlayback(notifyCompletion = false)
        val media = MediaPlayer()
        player = media
        activeFile = file
        playbackFinished = onPlaybackFinished
        media.setOnPreparedListener { prepared ->
            runCatching { prepared.start() }.onSuccess {
                if (continuation.isActive) continuation.resume(Unit)
            }.onFailure {
                finishPlayback(prepared, file)
                if (continuation.isActive) continuation.resumeWithException(it)
            }
        }
        media.setOnCompletionListener { completed ->
            finishPlayback(completed, file)
        }
        media.setOnErrorListener { failed, what, extra ->
            finishPlayback(failed, file)
            if (continuation.isActive) continuation.resumeWithException(
                IllegalStateException("Audio playback failed ($what/$extra)")
            )
            true
        }
        continuation.invokeOnCancellation {
            if (player === media) stopCurrentPlayback()
        }
        try {
            media.setDataSource(file.absolutePath)
            media.prepareAsync()
        } catch (error: Exception) {
            finishPlayback(media, file)
            if (continuation.isActive) continuation.resumeWithException(error)
        }
    }

    companion object {
        private const val CONNECT_TIMEOUT_MS = 15_000
        private const val READ_TIMEOUT_MS = 60_000
        private const val MAX_AUDIO_BYTES = 8 * 1024 * 1024
        private const val MAX_ERROR_BYTES = 16 * 1024
        private const val MAX_TEXT_CHARS = 5_000

        internal fun validateVoiceId(voiceId: String) {
            require(voiceId.matches(Regex("[A-Za-z0-9]{10,64}"))) { "ElevenLabs voice ID is invalid" }
        }

        internal fun buildRequest(text: String): JSONObject =
            JSONObject()
                .put("text", text)
                .put("model_id", "eleven_multilingual_v2")
                .put("voice_settings", JSONObject()
                    .put("stability", 0.42)
                    .put("similarity_boost", 0.82)
                    .put("style", 0.28)
                    .put("use_speaker_boost", true))
    }
}
