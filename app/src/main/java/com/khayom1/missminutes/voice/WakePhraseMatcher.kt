package com.khayom1.missminutes.voice

import java.util.Locale

/**
 * Parses a wake phrase and an optional command spoken in the same utterance.
 * Supports English, Russian and Tajik wake variants.
 */
object WakePhraseMatcher {
    private val separators = Regex("[^\\p{L}\\p{N}]+")
    private val whitespace = Regex("\\s+")

    // English + common spoken variants ("Miss Minutes", "hey Miss Minutes", "Miss Minute")
    private val englishWake = Regex(
        "^\\s*(?:hey\\s+)?miss[\\s,.;:!?-]+minutes?(?:\\b|[,.;:!?—-])?\\s*(.*)$",
        RegexOption.IGNORE_CASE
    )

    // Russian: "Мисс Минутс", "Мисс Минутка", "Мисс Минуты"
    private val russianWake = Regex(
        "^\\s*(?:эй\\s+)?мисс[\\s,.;:!?-]+минут(?:с|ка|ы)?(?:\\b|[,.;:!?—-])?\\s*(.*)$",
        setOf(RegexOption.IGNORE_CASE)
    )

    // Tajik: "Мисс Минутка", "Мисс Минутс"
    private val tajikWake = Regex(
        "^\\s*(?:эй\\s+)?мисс[\\s,.;:!?-]+минут(?:ка|с)?(?:\\b|[,.;:!?—-])?\\s*(.*)$",
        setOf(RegexOption.IGNORE_CASE)
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
            ?: russianWake.matchEntire(trimmed)
            ?: tajikWake.matchEntire(trimmed)
            ?: return null

        val command = match.groupValues.getOrNull(1)
            ?.trim()
            ?.removePrefix(",")
            ?.removePrefix(".")
            ?.trim()
            ?.takeIf { it.isNotEmpty() }

        return Match(command)
    }

    fun matches(transcript: String): Boolean = parse(transcript) != null

    /** Only the primary hypothesis may trigger a command. */
    fun parseRecognitionCandidates(candidates: List<String>): Match? =
        candidates.firstOrNull()?.let(::parse)

    fun matchesRecognitionCandidates(candidates: List<String>): Boolean =
        parseRecognitionCandidates(candidates) != null
}
