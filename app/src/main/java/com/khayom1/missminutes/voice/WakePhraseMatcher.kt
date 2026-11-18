package com.khayom1.missminutes.voice

import java.util.Locale

/** Parses a wake phrase and an optional command spoken in the same utterance. */
object WakePhraseMatcher {
    private val separators = Regex("[^\\p{L}\\p{N}]+")
    private val whitespace = Regex("\\s+")
    private val wakePrefix = Regex(
        "^\\s*miss[\\s,.;:!?-]+minutes\\b[\\s,.;:!?—-]*(.*)$",
        RegexOption.IGNORE_CASE
    )

    data class Match(val command: String?)

    fun normalize(transcript: String): String = transcript
        .lowercase(Locale.ROOT)
        .replace(separators, " ")
        .trim()
        .replace(whitespace, " ")

    fun parse(transcript: String): Match? {
        val match = wakePrefix.matchEntire(transcript) ?: return null
        val command = match.groupValues[1]
            .trim()
            .removePrefix(",")
            .trim()
            .takeIf { it.isNotEmpty() }
        return Match(command)
    }

    fun matches(transcript: String): Boolean = parse(transcript) != null

    /** Only the primary hypothesis may trigger a command. */
    fun parseRecognitionCandidates(candidates: List<String>): Match? =
        candidates.firstOrNull()?.let(::parse)

    fun matchesRecognitionCandidates(candidates: List<String>): Boolean =
        parseRecognitionCandidates(candidates) != null
}
