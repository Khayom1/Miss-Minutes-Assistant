package com.khayom1.missminutes

import org.json.JSONObject

/** Semantic movement intent chosen by the dialogue model, not an authored clip name. */
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
        private val gestures = setOf("none", "talk", "open_hands", "wave", "point", "shrug", "hands_together", "present")

        fun parse(raw: String, fallbackReply: String = raw): MotionIntent {
            val first = raw.indexOf('{')
            val last = raw.lastIndexOf('}')
            if (first >= 0 && last > first) runCatching {
                val json = JSONObject(raw.substring(first, last + 1))
                val mood = json.optString("mood", "neutral").lowercase().takeIf { it in moods } ?: "neutral"
                val gesture = json.optString("gesture", "none").lowercase().takeIf { it in gestures } ?: "none"
                val gaze = when (json.optString("gaze", "viewer").lowercase()) {
                    "left" -> -0.65f
                    "right" -> 0.65f
                    "up" -> 0.35f
                    "down" -> -0.25f
                    "wander" -> 0.2f
                    else -> 0f
                }
                return MotionIntent(
                    json.optString("reply").trim().ifBlank { fallbackReply },
                    mood,
                    gaze,
                    json.optDouble("head_tilt", 0.0).toFloat().coerceIn(-0.55f, 0.55f),
                    json.optDouble("body_lean", 0.0).toFloat().coerceIn(-0.22f, 0.22f),
                    gesture,
                    json.optDouble("energy", 0.35).toFloat().coerceIn(0f, 1f),
                    json.optDouble("emphasis", 0.25).toFloat().coerceIn(0f, 1f)
                )
            }
            val value = fallbackReply.lowercase()
            val mood = when {
                listOf("хаха", "хи-хи", "смеш", "😂", "laugh", "funny").any(value::contains) -> "happy"
                listOf("ого", "удив", "невероят", "wow", "surpris").any(value::contains) -> "surprised"
                listOf("сердит", "angry", "раздраж").any(value::contains) -> "angry"
                listOf("груст", "жаль", "sad").any(value::contains) -> "sad"
                listOf("интерес", "почему", "curious").any(value::contains) -> "curious"
                else -> "neutral"
            }
            val gesture = if (listOf("привет", "здравств", "hello", "welcome", "пока", "bye").any(value::contains)) "wave" else "none"
            return neutral(fallbackReply).copy(mood = mood, gesture = gesture)
        }

        fun neutral(reply: String = "") = MotionIntent(reply, "neutral", 0f, 0f, 0f, "none", 0.35f, 0.2f)
    }
}
