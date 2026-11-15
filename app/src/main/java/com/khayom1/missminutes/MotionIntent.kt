package com.khayom1.missminutes

import org.json.JSONObject

/** Semantic behaviour chosen by the dialogue model, independent of authored clips. */
data class MotionIntent(
    val reply: String,
    val mood: String,
    val gaze: Float,
    val headTilt: Float,
    val bodyLean: Float,
    val gesture: String,
    val energy: Float,
    val emphasis: Float
) {
    companion object {
        private val moods = setOf("neutral", "happy", "curious", "surprised", "angry", "sad", "sleepy", "playful")
        private val gazes = setOf("viewer", "left", "right", "up", "down", "wander")
        private val gestures = setOf("none", "open_hands", "wave", "point", "shrug", "hands_together", "present")

        fun parse(raw: String, fallbackReply: String = raw): MotionIntent {
            val start = raw.indexOf('{')
            val end = raw.lastIndexOf('}')
            if (start >= 0 && end > start) runCatching {
                return fromJson(JSONObject(raw.substring(start, end + 1)), fallbackReply)
            }
            return neutral(fallbackReply).copy(mood = inferMood(fallbackReply), gesture = inferGesture(fallbackReply))
        }

        private fun fromJson(json: JSONObject, fallbackReply: String): MotionIntent {
            val reply = json.optString("reply").trim().ifBlank { fallbackReply }
            val mood = json.optString("mood", "neutral").lowercase().takeIf { it in moods } ?: "neutral"
            val gazeName = json.optString("gaze", "viewer").lowercase().takeIf { it in gazes } ?: "viewer"
            val gesture = json.optString("gesture", "none").lowercase().takeIf { it in gestures } ?: "none"
            val gaze = when (gazeName) {
                "left" -> -0.65f; "right" -> 0.65f; "up" -> 0.35f; "down" -> -0.25f; "wander" -> 0.2f; else -> 0f
            }
            return MotionIntent(
                reply, mood, gaze,
                json.optDouble("head_tilt", 0.0).toFloat().coerceIn(-0.55f, 0.55f),
                json.optDouble("body_lean", 0.0).toFloat().coerceIn(-0.22f, 0.22f),
                gesture,
                json.optDouble("energy", 0.35).toFloat().coerceIn(0f, 1f),
                json.optDouble("emphasis", 0.25).toFloat().coerceIn(0f, 1f)
            )
        }

        fun inferMood(text: String): String {
            val value = text.lowercase()
            return when {
                listOf("хаха", "хи-хи", "смеш", "😂", "laugh", "funny").any(value::contains) -> "happy"
                listOf("ого", "удив", "невероят", "wow", "surpris").any(value::contains) -> "surprised"
                listOf("злюсь", "сердит", "angry", "раздраж").any(value::contains) -> "angry"
                listOf("жаль", "груст", "sad", "сожале").any(value::contains) -> "sad"
                listOf("интерес", "почему", "curious", "wonder").any(value::contains) -> "curious"
                else -> "neutral"
            }
        }

        fun inferGesture(text: String): String {
            val value = text.lowercase()
            return when {
                listOf("привет", "здравств", "hello", "welcome", "пока", "bye").any(value::contains) -> "wave"
                listOf("смотри", "обрати внимание", "look at", "вот").any(value::contains) -> "point"
                listOf("не знаю", "понятия не имею", "who knows").any(value::contains) -> "shrug"
                listOf("позволь показать", "представляю", "let me show").any(value::contains) -> "present"
                else -> "none"
            }
        }

        fun neutral(reply: String = "") = MotionIntent(reply, "neutral", 0f, 0f, 0f, "none", 0.35f, 0.2f)
    }
}
