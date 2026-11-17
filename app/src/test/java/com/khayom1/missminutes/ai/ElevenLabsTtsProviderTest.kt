package com.khayom1.missminutes.ai

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class ElevenLabsTtsProviderTest {
    @Test
    fun sendsAuthenticatedUserTokenAndReturnsMp3() = runBlocking {
        var sentHeaders: Map<String, String>? = null
        var sentBody = ""
        val provider = SupabaseElevenLabsTtsProvider(
            functionUrl = "https://project.supabase.co/functions/v1/miss-minutes-tts",
            publishableKey = "sb_publishable_public",
            accessTokenSource = AccessTokenSource { "user-session-jwt" },
            transport = object : TtsHttpTransport {
                override suspend fun post(
                    url: String,
                    headers: Map<String, String>,
                    body: String
                ): TtsHttpResponse {
                    sentHeaders = headers
                    sentBody = body
                    return TtsHttpResponse(200, "audio/mpeg", byteArrayOf(1, 2, 3))
                }
            }
        )
        assertArrayEquals(byteArrayOf(1, 2, 3), provider.synthesize("  Hello  "))
        assertEquals("Bearer user-session-jwt", sentHeaders?.get("Authorization"))
        assertEquals("sb_publishable_public", sentHeaders?.get("apikey"))
        assertEquals("audio/mpeg", sentHeaders?.get("Accept"))
        assertEquals("""{"text":"Hello"}""", sentBody)
    }

    @Test
    fun refusesToCallWithoutUserSession() = runBlocking {
        var calls = 0
        val provider = SupabaseElevenLabsTtsProvider(
            "https://project.supabase.co/functions/v1/miss-minutes-tts",
            "sb_publishable_public",
            AccessTokenSource { null },
            object : TtsHttpTransport {
                override suspend fun post(url: String, headers: Map<String, String>, body: String): TtsHttpResponse {
                    calls++
                    return TtsHttpResponse(200, "audio/mpeg", byteArrayOf(1))
                }
            }
        )
        assertThrows(AiProviderException.AuthenticationRequired::class.java) {
            runBlocking { provider.synthesize("Hello") }
        }
        assertEquals(0, calls)
    }

    @Test
    fun rejectsTextOutsideBoundsBeforeNetworkCall() = runBlocking {
        var calls = 0
        val provider = SupabaseElevenLabsTtsProvider(
            "https://project.supabase.co/functions/v1/miss-minutes-tts",
            "sb_publishable_public",
            AccessTokenSource { "user-jwt" },
            object : TtsHttpTransport {
                override suspend fun post(url: String, headers: Map<String, String>, body: String): TtsHttpResponse {
                    calls++
                    return TtsHttpResponse(200, "audio/mpeg", byteArrayOf(1))
                }
            }
        )
        assertThrows(IllegalArgumentException::class.java) {
            runBlocking { provider.synthesize(" ") }
        }
        assertEquals(0, calls)
    }

    @Test
    fun mapsRateLimitAndRejectsNonAudioResponse() = runBlocking {
        fun provider(status: Int, type: String?) = SupabaseElevenLabsTtsProvider(
            "https://project.supabase.co/functions/v1/miss-minutes-tts",
            "sb_publishable_public",
            AccessTokenSource { "user-jwt" },
            object : TtsHttpTransport {
                override suspend fun post(url: String, headers: Map<String, String>, body: String) =
                    TtsHttpResponse(status, type, byteArrayOf(1))
            }
        )
        assertThrows(TtsProviderException::class.java) {
            runBlocking { provider(429, "application/json").synthesize("Hello") }
        }
        assertThrows(TtsProviderException::class.java) {
            runBlocking { provider(200, "application/json").synthesize("Hello") }
        }
    }
}
