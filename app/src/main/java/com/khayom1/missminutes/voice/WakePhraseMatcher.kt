package com.khayom1.missminutes.voice

import java.util.Locale

/** Exact phrase gate. Extra words (including "Hey") are intentionally rejected. */
object WakePhraseMatcher {
    private val separators = Regex("[^\\p{L}\\p{N}]+")
    private val whitespace = Regex("\\s+")

    fun normalize(transcript: String): String = transcript
        .lowercase(Locale.ROOT)
        .replace(separators, " ")
        .trim()
        .replace(whitespace, " ")

    fun matches(transcript: String): Boolean = normalize(transcript) == "miss minutes"
}
