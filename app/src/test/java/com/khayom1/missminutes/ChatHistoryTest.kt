package com.khayom1.missminutes

import org.junit.Assert.assertEquals
import org.junit.Test

class ChatHistoryTest {
    @Test fun visibleHistoryRetainsOnlyTheMostRecentHundredMessages() {
        val messages = mutableListOf<ChatLine>()
        repeat(150) { appendMessage(messages, ChatLine("user", "message-$it")) }
        assertEquals(MAX_VISIBLE_MESSAGES, messages.size)
        assertEquals("message-50", messages.first().text)
        assertEquals("message-149", messages.last().text)
    }
}
