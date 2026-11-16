package com.khayom1.missminutes.ai

import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

object SupabaseEndpointPolicy {
    fun parse(value: String?): HttpUrl? {
        val url = value?.trim()?.toHttpUrlOrNull() ?: return null
        if (url.scheme != "https") return null
        if (url.username.isNotEmpty() || url.password.isNotEmpty()) return null
        if (url.port != 443) return null
        if (url.encodedPath !in setOf("", "/")) return null
        if (url.encodedQuery != null || url.encodedFragment != null) return null
        return url
    }
}

object BackendEndpointPolicy {
    fun parse(value: String?, supabaseBaseUrl: String?): HttpUrl? {
        val url = value?.trim()?.toHttpUrlOrNull() ?: return null
        val expectedBase = SupabaseEndpointPolicy.parse(supabaseBaseUrl) ?: return null
        if (url.scheme != "https" || url.host != expectedBase.host || url.port != 443) return null
        if (url.username.isNotEmpty() || url.password.isNotEmpty()) return null
        if (url.encodedPath != "/functions/v1/deepseek-proxy") return null
        if (url.encodedQuery != null || url.encodedFragment != null) return null
        return url
    }
}

object LocalInferenceEndpointPolicy {
    private val loopbackHosts = setOf("127.0.0.1", "localhost", "::1")

    fun parse(value: String?): HttpUrl? {
        val url = value?.trim()?.toHttpUrlOrNull() ?: return null
        if (url.scheme !in setOf("http", "https")) return null
        if (url.host !in loopbackHosts) return null
        if (url.username.isNotEmpty() || url.password.isNotEmpty()) return null
        if (url.encodedPath != "/v1/chat/completions") return null
        if (url.encodedQuery != null || url.encodedFragment != null) return null
        return url
    }
}
