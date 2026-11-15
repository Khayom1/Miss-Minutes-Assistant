package com.khayom1.missminutes

import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.InputStream

/** Reads network payloads with a hard cap so a malformed or hostile response cannot exhaust app memory. */
internal fun InputStream.readBoundedBytes(maxBytes: Int): ByteArray {
    require(maxBytes > 0) { "maxBytes must be positive" }
    val output = ByteArrayOutputStream(minOf(maxBytes, 8 * 1024))
    val buffer = ByteArray(8 * 1024)
    var total = 0
    while (true) {
        val count = read(buffer)
        if (count < 0) break
        if (count == 0) {
            val single = read()
            if (single < 0) break
            if (total == maxBytes) throw IOException("Response exceeds the $maxBytes-byte safety limit")
            output.write(single)
            total++
            continue
        }
        if (total > maxBytes - count) {
            throw IOException("Response exceeds the $maxBytes-byte safety limit")
        }
        output.write(buffer, 0, count)
        total += count
    }
    return output.toByteArray()
}

internal fun InputStream.readBoundedText(maxBytes: Int): String =
    readBoundedBytes(maxBytes).toString(Charsets.UTF_8)
