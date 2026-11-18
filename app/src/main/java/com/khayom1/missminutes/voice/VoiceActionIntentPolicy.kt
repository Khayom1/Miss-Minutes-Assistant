package com.khayom1.missminutes.voice

import java.util.Locale

/**
 * Grants the voice runtime's one-shot tool confirmation only when the user's
 * original utterance explicitly requests that exact consequential action.
 * Model-selected tools and untrusted screen text can never create this grant.
 */
internal object VoiceActionIntentPolicy {
    private val separators = Regex("[^\\p{L}\\p{N}]+")
    private val whitespace = Regex("\\s+")

    private val negations = listOf(
        "do not", "don't", "dont", "never", "not", "no", "stop", "not necessary",
        "не надо", "не нужно", "не хочу", "не делай", "не", "нельзя",
        "накун", "нагузор", "намон", "на"
    ).map(::normalize)

    private val allowedRequestPrefixTokens = setOf(
        "please", "can", "could", "would", "will", "you", "hey", "kindly",
        "пожалуйста", "можешь", "можно", "ты", "вы",
        "илтимос", "лутфан", "метавонӣ", "метавонед", "маро", "ба", "ман",
        "соати", "пагоҳ", "фардо", "имрӯз", "шаш", "ҳафт", "ҳашт", "панҷ",
        "чор", "се", "ду", "як", "нӯҳ", "даҳ"
    )

    fun isExplicitlyRequested(actionId: String, utterance: String): Boolean {
        val command = normalize(utterance)
        if (command.isBlank()) return false

        val phrases = when (actionId) {
            "create_reminder" -> listOf(
                "remind me",
                "set a reminder",
                "create a reminder",
                "add a reminder",
                "schedule a reminder",
                "reminder for",
                "напомни мне",
                "напомни",
                "поставь напоминание",
                "создай напоминание",
                "добавь напоминание",
                "установи напоминание",
                "ёдрас кун",
                "ёдрас кунед",
                "хотиррасон кун",
                "хотиррасон кунед",
                "ёдовар кун",
                "дар хотир дор"
            )
            "set_alarm" -> listOf(
                "set an alarm",
                "wake me",
                "alarm for",
                "поставь будильник",
                "установи будильник",
                "разбуди меня",
                "будильник на",
                "маро бедор кун",
                "бедор кун",
                "зангдор гузор",
                "соати зангдор"
            )
            "open_app" -> listOf(
                "open",
                "launch",
                "открой",
                "открыть",
                "запусти",
                "запустить",
                "барномаро кушо",
                "барнома кушо",
                "кушо"
            )
            else -> return false
        }

        return phrases.asSequence()
            .map(::normalize)
            .filter(String::isNotBlank)
            .any { phrase -> containsNonNegatedPhrase(command, phrase) }
    }

    private fun containsNonNegatedPhrase(command: String, phrase: String): Boolean {
        val padded = " $command "
        val needle = " $phrase "
        var searchFrom = 0
        while (true) {
            val index = padded.indexOf(needle, startIndex = searchFrom)
            if (index < 0) return false
            val prefix = padded.substring(0, index)
            val prefixTokens = prefix.trim().split(' ').filter(String::isNotBlank)
            val isDirectRequest = prefixTokens.size <= 3 &&
                prefixTokens.all { it in allowedRequestPrefixTokens }
            val isNegated = negations.any { negation -> prefix.contains(" $negation ") }
            if (isDirectRequest && !isNegated) return true
            searchFrom = index + needle.length
        }
    }

    private fun normalize(value: String): String = value
        .lowercase(Locale.ROOT)
        .replace('’', '\'')
        .replace(separators, " ")
        .trim()
        .replace(whitespace, " ")
}
