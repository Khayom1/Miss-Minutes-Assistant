package com.khayom1.missminutes.ai

import android.content.Context
import android.media.MediaPlayer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import okhttp3.Headers
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.File
import java.io.IOException
import java.util.concurrent.TimeUnit

class TtsProviderException(message: String) : Exception(message)
interface TtsProvider { suspend fun synthesize(text: String): ByteArray }
data class TtsHttpResponse(val statusCode: Int, val contentType: String?, val body: ByteArray)
interface TtsHttpTransport {
    suspend fun post(url: String, headers: Map<String, String>, body: String): TtsHttpResponse
}
@Serializable
private data class TtsRequest(val text: String)

/** The ElevenLabs credential and voice selection remain on the server. */
class SupabaseElevenLabsTtsProvider(
    private val functionUrl: String,
    private val publishableKey: String,
    private val accessTokenSource: AccessTokenSource,
    private val transport: TtsHttpTransport,
    private val json: Json = Json
) : TtsProvider {
    init {
        require(functionUrl.startsWith("https://"))
        require(publishableKey.isNotBlank())
    }

    override suspend fun synthesize(text: String): ByteArray {
        val normalized = text.trim()
        require(normalized.isNotEmpty() && normalized.length <= MAX_TEXT_CHARS)
        val token = accessTokenSource.currentAccessToken()?.takeIf { it.isNotBlank() }
            ?: throw AiProviderException.AuthenticationRequired()
        val response = try {
            transport.post(
                functionUrl,
                mapOf(
                    "Authorization" to "Bearer $token",
                    "apikey" to publishableKey,
                    "Content-Type" to "application/json",
                    "Accept" to "audio/mpeg"
                ),
                json.encodeToString(TtsRequest(normalized))
            )
        } catch (error: IOException) {
            throw TtsProviderException("Voice service unavailable.")
        }
        when (response.statusCode) {
            200 -> if (response.contentType?.startsWith("audio/") != true || response.body.isEmpty()) {
                throw TtsProviderException("Invalid audio response.")
            }
            401, 403 -> throw AiProviderException.AuthenticationRequired()
            429 -> throw TtsProviderException("Voice request limit reached.")
            in 500..599 -> throw TtsProviderException("Voice service unavailable.")
            else -> throw TtsProviderException("Voice request failed.")
        }
        if (response.body.size > MAX_AUDIO_BYTES) throw TtsProviderException("Audio response too large.")
        return response.body
    }

    private companion object {
        const val MAX_TEXT_CHARS = 3_000
        const val MAX_AUDIO_BYTES = 5 * 1024 * 1024
    }
}

class OkHttpTtsHttpTransport(
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .callTimeout(70, TimeUnit.SECONDS)
        .build()
) : TtsHttpTransport {
    override suspend fun post(url: String, headers: Map<String, String>, body: String): TtsHttpResponse =
        withContext(Dispatchers.IO) {
            val requestHeaders = Headers.Builder().apply {
                headers.forEach { (name, value) -> add(name, value) }
            }.build()
            val request = Request.Builder()
                .url(url)
                .headers(requestHeaders)
                .post(body.toRequestBody("application/json; charset=utf-8".toMediaType()))
                .build()
            client.newCall(request).execute().use { response ->
                val bytes = response.body?.source()?.use { source ->
                    if (source.request(MAX_AUDIO_BYTES + 1L)) throw TtsProviderException("Audio response too large.")
                    source.readByteArray()
                } ?: byteArrayOf()
                TtsHttpResponse(response.code, response.header("Content-Type"), bytes)
            }
        }

    private companion object { const val MAX_AUDIO_BYTES = 5L * 1024 * 1024 }
}

/** One-at-a-time MP3 playback; temporary cache files are deleted on every exit path. */
class AndroidTtsPlayback(context: Context) : AutoCloseable {
    private val appContext = context.applicationContext
    private var player: MediaPlayer? = null
    private var audioFile: File? = null

    suspend fun play(audio: ByteArray, onCompleted: () -> Unit, onError: () -> Unit) {
        stop()
        val file = withContext(Dispatchers.IO) {
            File.createTempFile("miss-minutes-tts-", ".mp3", appContext.cacheDir).apply { writeBytes(audio) }
        }
        withContext(Dispatchers.Main.immediate) {
            try {
                val media = MediaPlayer()
                player = media
                audioFile = file
                media.setDataSource(file.absolutePath)
                media.setOnPreparedListener { it.start() }
                media.setOnCompletionListener { release(); onCompleted() }
                media.setOnErrorListener { _, _, _ -> release(); onError(); true }
                media.prepareAsync()
            } catch (_: Exception) {
                release()
                onError()
            }
        }
    }

    fun stop() {
        player?.let { media -> runCatching { media.stop() } }
        release()
    }

    private fun release() {
        player?.let { media ->
            runCatching { media.reset() }
            media.release()
        }
        player = null
        audioFile?.delete()
        audioFile = null
    }

    override fun close() = stop()
}
