package com.khayom1.missminutes.ai

import java.io.ByteArrayOutputStream
import java.io.IOException
import java.net.ServerSocket
import java.net.Socket
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread
import kotlinx.coroutines.async
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.runBlocking
import okhttp3.EventListener
import okhttp3.OkHttpClient
import org.junit.Assert.assertTrue
import org.junit.Test

class OkHttpAiHttpTransportTest {
    @Test
    fun productionTransportDoesNotReplayCompletionRequestsAutomatically() {
        assertTrue(!OkHttpAiHttpTransport().client.retryOnConnectionFailure)
    }

    @Test
    fun callTimeoutIsReportedAsTimeout() = runBlocking {
        val server = BlockingHttpServer()
        val client = OkHttpClient.Builder()
            .callTimeout(500, TimeUnit.MILLISECONDS)
            .build()
        try {
            val error = runCatching {
                OkHttpAiHttpTransport(client).post(
                    server.url,
                    mapOf("Content-Type" to "application/json"),
                    """{"prompt":"test"}"""
                )
            }.exceptionOrNull()
            assertTrue(server.requestReceived.await(2, TimeUnit.SECONDS))
            assertTrue(error is AiProviderException.Timeout)
        } finally {
            server.close()
        }
    }

    @Test
    fun coroutineCancellationCancelsTheUnderlyingOkHttpCall() = runBlocking {
        val server = BlockingHttpServer()
        val callStarted = CountDownLatch(1)
        val underlyingCall = java.util.concurrent.atomic.AtomicReference<okhttp3.Call?>()
        val client = OkHttpClient.Builder()
            .callTimeout(60, TimeUnit.SECONDS)
            .eventListener(object : EventListener() {
                override fun callStart(call: okhttp3.Call) {
                    underlyingCall.set(call)
                    callStarted.countDown()
                }
            })
            .build()
        try {
            val request = async(Dispatchers.IO) {
                OkHttpAiHttpTransport(client).post(
                    server.url,
                    mapOf("Content-Type" to "application/json"),
                    """{"prompt":"cancel me"}"""
                )
            }
            awaitCallStarted(callStarted)
            val call = checkNotNull(underlyingCall.get()) { "EventListener did not capture the active call." }
            awaitRequestReceived(server.requestReceived)
            awaitResponseWithheld(server.responseWithheld)
            request.cancelAndJoin()
            assertCallWasCancelled(call)
        } finally {
            server.close()
        }
    }

    private fun awaitCallStarted(signal: CountDownLatch) {
        assertTrue("OkHttp call did not start.", signal.await(5, TimeUnit.SECONDS))
    }

    private fun awaitRequestReceived(signal: CountDownLatch) {
        assertTrue("The local server did not receive the request.", signal.await(5, TimeUnit.SECONDS))
    }

    private fun awaitResponseWithheld(signal: CountDownLatch) {
        assertTrue("The test server did not withhold its response.", signal.await(5, TimeUnit.SECONDS))
    }

    private fun assertCallWasCancelled(call: okhttp3.Call) {
        assertTrue("Coroutine cancellation must cancel the active OkHttp Call.", call.isCanceled())
    }

    private class BlockingHttpServer : AutoCloseable {
        private val server = ServerSocket(0)
        @Volatile private var acceptedSocket: Socket? = null
        val requestReceived = CountDownLatch(1)
        val responseWithheld = CountDownLatch(1)
        private val releaseResponse = CountDownLatch(1)
        val url: String = "http://127.0.0.1:${server.localPort}/chat"

        private val worker = thread(name = "ai-provider-test-server", isDaemon = true) {
            try {
                server.accept().use { socket ->
                    acceptedSocket = socket
                    socket.soTimeout = 10_000
                    val input = socket.getInputStream()
                    val headers = ByteArrayOutputStream()
                    val marker = byteArrayOf(13, 10, 13, 10)
                    var matched = 0
                    while (matched < marker.size) {
                        val next = input.read()
                        if (next < 0) return@use
                        headers.write(next)
                        matched = if (next.toByte() == marker[matched]) matched + 1 else {
                            if (next.toByte() == marker[0]) 1 else 0
                        }
                    }
                    val headerText = headers.toByteArray().toString(Charsets.ISO_8859_1)
                    val contentLength = Regex("(?im)^Content-Length: (\\d+)")
                        .find(headerText)?.groupValues?.get(1)?.toIntOrNull() ?: 0
                    repeat(contentLength) {
                        if (input.read() < 0) return@use
                    }
                    requestReceived.countDown()
                    // Hold the connection open deterministically: relying on a blocking
                    // socket read here can observe a client half-close and complete the
                    // request before the cancellation assertion runs.
                    responseWithheld.countDown()
                    releaseResponse.await(30, TimeUnit.SECONDS)
                }
            } catch (_: Exception) {
                // Disconnects are expected in these tests.
            }
        }

        override fun close() {
            releaseResponse.countDown()
            runCatching { server.close() }
            runCatching { acceptedSocket?.close() }
            worker.join(1_000)
        }
    }
}
