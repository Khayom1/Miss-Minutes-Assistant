package com.khayom1.missminutes.voice

import java.util.Locale

/**
 * Parses a wake phrase and an optional command spoken in the same utterance.
 * Supports English, Russian and Tajik wake variants, including “Мисс минутка”.
 */
object WakePhraseMatcher {
    private val separators = Regex("[^\\p{L}\\p{N}]+")
    private val whitespace = Regex("\\s+")

    private val trailingWakeSeparator = """[\s,.;:!?—–-]*"""
    private val englishWake = Regex(
        """^\s*(?:hey\s+)?miss[\s,.;:!?-]+minutes?(?:\b)?$trailingWakeSeparator(.*)$""",
        RegexOption.IGNORE_CASE
    )
    private val cyrillicWake = Regex(
        """^\s*(?:эй\s+|hey\s+)?мисс[\s,.;:!?-]+минут(?:ка|с|ы)?(?=$|[\s,.;:!?—–-])$trailingWakeSeparator(.*)$""",
        RegexOption.IGNORE_CASE
    )
    private val compactCyrillicWake = Regex(
        """^\s*(?:эй\s+)?мис+с?\s*минут(?:ка|с|ы)?(?=$|[\s,.;:!?—–-])$trailingWakeSeparator(.*)$""",
        RegexOption.IGNORE_CASE
    )

    data class Match(val command: String?)

    fun normalize(transcript: String): String = transcript
        .lowercase(Locale.ROOT)
        .replace(separators, " ")
        .trim()
        .replace(whitespace, " ")

    fun parse(transcript: String): Match? {
        val trimmed = transcript.trim()
        if (trimmed.isEmpty()) return null

        val match = englishWake.matchEntire(trimmed)
            ?: cyrillicWake.matchEntire(trimmed)
            ?: compactCyrillicWake.matchEntire(trimmed)
            ?: return null

        val command = match.groupValues.getOrNull(1)
            ?.trim()
            ?.trimStart(',', '.', ':', ';', '!', '?', '—', '–', '-')
            ?.trim()
            ?.takeIf { it.isNotEmpty() }

        return Match(command)
    }

    fun matches(transcript: String): Boolean = parse(transcript) != null

    fun parseRecognitionCandidates(candidates: List<String>): Match? =
        candidates.firstOrNull()?.let(::parse)

    fun matchesRecognitionCandidates(candidates: List<String>): Boolean =
        parseRecognitionCandidates(candidates) != null
}
