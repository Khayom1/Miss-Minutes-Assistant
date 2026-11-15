package com.khayom1.missminutes

import java.io.ByteArrayInputStream
import java.io.IOException
import java.io.InputStream
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test

class NetworkIoTest {
    @Test fun acceptsPayloadAtExactLimit() {
        val payload = byteArrayOf(1, 2, 3, 4)
        assertArrayEquals(payload, ByteArrayInputStream(payload).readBoundedBytes(4))
    }
    @Test(expected = IOException::class) fun rejectsPayloadOverLimit() {
        ByteArrayInputStream(byteArrayOf(1, 2, 3, 4, 5)).readBoundedBytes(4)
    }
    @Test fun decodesUtf8WithinLimit() {
        assertEquals("Минутка", ByteArrayInputStream("Минутка".toByteArray(Charsets.UTF_8)).readBoundedText(32))
    }
    @Test fun handlesStreamsThatReturnZeroLengthReads() {
        val stream = object : InputStream() {
            private var first = true
            override fun read(): Int = 65
            override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
                if (first) { first = false; return 0 }
                return -1
            }
        }
        assertArrayEquals(byteArrayOf(65), stream.readBoundedBytes(1))
    }
    @Test(expected = IllegalArgumentException::class) fun rejectsNonPositiveLimit() {
        ByteArrayInputStream(byteArrayOf(1)).readBoundedBytes(0)
    }
}
