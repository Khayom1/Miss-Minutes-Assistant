package com.khayom1.missminutes

import java.io.ByteArrayInputStream
import java.io.IOException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertArrayEquals
import org.junit.Test

class NetworkIoTest {
    @Test
    fun acceptsPayloadAtExactLimit() {
        val payload = byteArrayOf(1, 2, 3, 4)
        assertArrayEquals(payload, ByteArrayInputStream(payload).readBoundedBytes(4))
    }

    @Test(expected = IOException::class)
    fun rejectsPayloadOverLimit() {
        ByteArrayInputStream(byteArrayOf(1, 2, 3, 4, 5)).readBoundedBytes(4)
    }

    @Test
    fun decodesUtf8WithinLimit() {
        assertEquals("Минутка", ByteArrayInputStream("Минутка".toByteArray(Charsets.UTF_8))
            .readBoundedText(32))
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsNonPositiveLimit() {
        ByteArrayInputStream(byteArrayOf(1)).readBoundedBytes(0)
    }
}
