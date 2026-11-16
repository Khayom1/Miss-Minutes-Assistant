package com.khayom1.missminutes.ai

import kotlinx.coroutines.suspendCancellableCoroutine
import okhttp3.Call
import okhttp3.Callback
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import okio.Buffer
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume

data class TransportResponse(
    val statusCode: Int,
    val body: String?,
    val failure: Throwable? = null
)

interface HttpPostTransport {
    suspend fun post(
        url: okhttp3.HttpUrl,
        headers: Map<String, String>,
        jsonBody: String
    ): TransportResponse
}

class OkHttpPostTransport(
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(10, TimeUnit.SECONDS)
        .callTimeout(35, TimeUnit.SECONDS)
        .followRedirects(false)
        .followSslRedirects(false)
        .retryOnConnectionFailure(false)
        .build()
) : HttpPostTransport {

    override suspend fun post(
        url: okhttp3.HttpUrl,
        headers: Map<String, String>,
        jsonBody: String
    ): TransportResponse = suspendCancellableCoroutine { continuation ->
        val requestBuilder = Request.Builder()
            .url(url)
            .post(jsonBody.toRequestBody(JSON_MEDIA_TYPE))

        headers.forEach { (name, value) ->
            requestBuilder.header(name, value)
        }

        val call = client.newCall(requestBuilder.build())
        continuation.invokeOnCancellation { call.cancel() }

        call.enqueue(object : Callback {
            override fun onFailure(call: Call, error: IOException) {
                if (continuation.isActive) {
                    continuation.resume(TransportResponse(0, null, error))
                }
            }

            override fun onResponse(call: Call, response: Response) {
                val result = response.use(::readBoundedResponse)
                if (continuation.isActive) {
                    continuation.resume(result)
                }
            }
        })
    }

    private fun readBoundedResponse(response: Response): TransportResponse {
        val body = response.body ?: return TransportResponse(response.code, "")
        if (body.contentLength() > MAX_RESPONSE_BYTES) {
            return TransportResponse(
                response.code,
                null,
                IllegalStateException("response_too_large")
            )
        }

        val source = body.source()
        val buffer = Buffer()
        var totalBytes = 0L

        try {
            while (totalBytes <= MAX_RESPONSE_BYTES) {
                val remaining = MAX_RESPONSE_BYTES + 1 - totalBytes
                val read = source.read(buffer, minOf(4_096L, remaining))
                if (read == -1L) break
                totalBytes += read
            }
        } catch (error: IOException) {
            return TransportResponse(response.code, null, error)
        }

        if (totalBytes > MAX_RESPONSE_BYTES) {
            return TransportResponse(
                response.code,
                null,
                IllegalStateException("response_too_large")
            )
        }

        return TransportResponse(response.code, buffer.readUtf8())
    }

    private companion object {
        val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()
        const val MAX_RESPONSE_BYTES = 32L * 1024L
    }
}
