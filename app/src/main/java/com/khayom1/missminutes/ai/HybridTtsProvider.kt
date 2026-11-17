package com.khayom1.missminutes.ai

import android.content.Context
import android.media.MediaPlayer
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.File
import java.io.IOException
import java.util.concurrent.TimeUnit

/**
 * Hosted ElevenLabs TTS through the authenticated Supabase Edge Function.
 * The ElevenLabs credential stays in Supabase secrets; this Android client
 * sends only the user's short-lived Supabase access token and public key.
 * If the hosted service is unavailable, playback fails closed to local,
 * offline-only Android TTS.
 */
class HybridTtsProvider(
    context: Context,
    private val functionUrl: String,
    private val publishableKey: String,
    private val accessTokenSource: AccessTokenSource,
    private val offlineFallback: TtsProvider,
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(65, TimeUnit.SECONDS)
        .callTimeout(70, TimeUnit.SECONDS)
        .build()
) : TtsProvider {
    private val appContext = context.applicationContext
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var requestJob: Job? = null
    private var player: MediaPlayer? = null
    private var audioFile: File? = null
    private var generation = 0L
    private var closed = false

    init {
        require(functionUrl.startsWith("https://")) { "TTS function URL must use HTTPS." }
        require(publishableKey.isNotBlank()) { "Supabase publishable key is required." }
    }

    override fun speak(text: String, onDone: () -> Unit, onError: () -> Unit) {
        val normalized = text.trim().take(MAX_TEXT_CHARS)
        if (closed || normalized.isEmpty()) {
            onError()
            return
        }
        stop()
        val requestGeneration = generation
        requestJob = scope.launch {
            try {
                val token = accessTokenSource.currentAccessToken()
                    ?.takeIf { it.isNotBlank() }
                    ?: throw IOException("A signed-in session is required for hosted voice.")
                val file = withContext(Dispatchers.IO) {
                    downloadAudio(normalized, token)
                }
                if (requestGeneration != generation || closed) {
                    file.delete()
                    return@launch
                }
                audioFile = file
                val media = MediaPlayer()
                player = media
                media.setOnPreparedListener { prepared ->
                    if (requestGeneration == generation && !closed) prepared.start()
                    else releasePlayer()
                }
                media.setOnCompletionListener {
                    if (requestGeneration == generation) {
                        releasePlayer()
                        onDone()
                    }
                }
                media.setOnErrorListener { _, _, _ ->
                    if (requestGeneration == generation) {
                        releasePlayer()
                        playOffline(normalized, requestGeneration, onDone, onError)
                    }
                    true
                }
                media.setDataSource(file.absolutePath)
                media.prepareAsync()
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                if (requestGeneration == generation && !closed) {
                    playOffline(normalized, requestGeneration, onDone, onError)
                }
            }
        }
    }

    private fun downloadAudio(text: String, token: String): File {
        val payload = org.json.JSONObject().put("text", text).toString()
        val request = Request.Builder()
            .url(functionUrl)
            .header("Authorization", "Bearer $token")
            .header("apikey", publishableKey)
            .header("Accept", "audio/mpeg")
            .post(payload.toRequestBody("application/json; charset=utf-8".toMediaType()))
            .build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw IOException("Hosted TTS request failed.")
            val body = response.body ?: throw IOException("Hosted TTS returned no audio.")
            val contentType = body.contentType()?.toString().orEmpty()
            if (!contentType.lowercase().startsWith("audio/")) {
                throw IOException("Hosted TTS returned an invalid content type.")
            }
            if (body.contentLength() > MAX_AUDIO_BYTES) {
                throw IOException("Hosted TTS response exceeded the audio limit.")
            }
            val destination = File.createTempFile("miss-minutes-voice-", ".mp3", appContext.cacheDir)
            try {
                body.byteStream().use { input ->
                    destination.outputStream().use { output ->
                        val buffer = ByteArray(8 * 1024)
                        var total = 0
                        while (true) {
                            val count = input.read(buffer)
                            if (count < 0) break
                            total += count
                            if (total > MAX_AUDIO_BYTES) {
                                throw IOException("Hosted TTS response exceeded the audio limit.")
                            }
                            output.write(buffer, 0, count)
                        }
                    }
                }
                if (destination.length() == 0L) throw IOException("Hosted TTS returned empty audio.")
                destination
            } catch (error: Exception) {
                destination.delete()
                throw error
            }
        }
    }

    private fun playOffline(
        text: String,
        requestGeneration: Long,
        onDone: () -> Unit,
        onError: () -> Unit
    ) {
        if (requestGeneration != generation || closed) return
        releasePlayer()
        offlineFallback.speak(
            text = text,
            onDone = { if (requestGeneration == generation) onDone() },
            onError = { if (requestGeneration == generation) onError() }
        )
    }

    private fun releasePlayer() {
        player?.let { media ->
            runCatching { media.setOnPreparedListener(null) }
            runCatching { media.setOnCompletionListener(null) }
            runCatching { media.setOnErrorListener(null) }
            runCatching { media.stop() }
            runCatching { media.reset() }
            runCatching { media.release() }
        }
        player = null
        audioFile?.delete()
        audioFile = null
    }

    override fun stop() {
        generation += 1
        requestJob?.cancel()
        requestJob = null
        releasePlayer()
        offlineFallback.stop()
    }

    override fun close() {
        if (closed) return
        stop()
        closed = true
        offlineFallback.close()
        scope.cancel()
    }

    private companion object {
        const val MAX_TEXT_CHARS = 3_000
        const val MAX_AUDIO_BYTES = 5 * 1024 * 1024
    }
}
