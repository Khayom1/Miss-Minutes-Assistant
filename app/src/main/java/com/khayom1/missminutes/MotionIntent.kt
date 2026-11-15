package com.khayom1.missminutes

/** Semantic movement intent selected by the dialogue model. */
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

        private fun jsonString(source: String, key: String): String? {
            val pattern = Regex(Regex.escape("\"" + key + "\"") + "\\s*:\\s*\"((?:\\\\.|[^\"\\\\])*)\"")
            val value = pattern.find(source)?.groupValues?.getOrNull(1) ?: return null
            return value.replace("\\\"", "\"")
                .replace("\\n", "\n")
                .replace("\\r", "\r")
                .replace("\\t", "\t")
                .replace("\\\\", "\\")
        }

        private fun jsonNumber(source: String, key: String, fallback: Double): Double {
            val pattern = Regex(Regex.escape("\"" + key + "\"") + "\\s*:\\s*(-?(?:\\d+(?:\\.\\d*)?|\\.\\d+)(?:[eE][+-]?\\d+)?)")
            return pattern.find(source)?.groupValues?.getOrNull(1)?.toDoubleOrNull() ?: fallback
        }

        fun parse(raw: String, fallbackReply: String = raw): MotionIntent {
            val first = raw.indexOf('{')
            val last = raw.lastIndexOf('}')
            if (first >= 0 && last > first) {
                val json = raw.substring(first, last + 1)
                val mood = jsonString(json, "mood")?.lowercase()?.takeIf { it in moods } ?: "neutral"
                val gesture = jsonString(json, "gesture")?.lowercase()?.takeIf { it in gestures } ?: "none"
                val gaze = when (jsonString(json, "gaze")?.lowercase()) {
                    "left" -> -0.65f
                    "right" -> 0.65f
                    "up" -> 0.35f
                    "down" -> -0.25f
                    "wander" -> 0.2f
                    else -> 0f
                }
                val reply = jsonString(json, "reply")?.trim().orEmpty().ifBlank { fallbackReply }
                return MotionIntent(
                    reply = reply,
                    mood = mood,
                    gaze = gaze,
                    headTilt = jsonNumber(json, "head_tilt", 0.0).toFloat().coerceIn(-0.55f, 0.55f),
                    bodyLean = jsonNumber(json, "body_lean", 0.0).toFloat().coerceIn(-0.22f, 0.22f),
                    gesture = gesture,
                    energy = jsonNumber(json, "energy", 0.35).toFloat().coerceIn(0f, 1f),
                    emphasis = jsonNumber(json, "emphasis", 0.25).toFloat().coerceIn(0f, 1f)
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
