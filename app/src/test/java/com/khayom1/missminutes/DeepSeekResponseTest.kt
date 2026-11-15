package com.khayom1.missminutes

import org.junit.Assert.assertEquals
import org.junit.Test

class DeepSeekResponseTest {
    @Test fun extractsOnlyCompletedAssistantContent() {
        val payload = """{"choices":[{"finish_reason":"stop","message":{"role":"assistant","content":"{\\\"reply\\\":\\\"Hello\\\"}"}}]}"""
        assertEquals("{\\\"reply\\\":\\\"Hello\\\"}", parseDeepSeekChatCompletion(payload))
    }
    @Test(expected = IllegalStateException::class)
    fun rejectsTruncatedCompletion() {
        parseDeepSeekChatCompletion("""{"choices":[{"finish_reason":"length","message":{"content":"partial"}}]}""")
    }
    @Test(expected = IllegalStateException::class)
    fun rejectsEmptyChoices() { parseDeepSeekChatCompletion("""{"choices":[]}""") }
    @Test(expected = IllegalStateException::class)
    fun rejectsMalformedPayload() { parseDeepSeekChatCompletion("not-json") }
    @Test(expected = IllegalStateException::class)
    fun rejectsEmptyAssistantContent() {
        parseDeepSeekChatCompletion("""{"choices":[{"finish_reason":"stop","message":{"content":" "}}]}""")
    }
}
