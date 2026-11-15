package com.khayom1.missminutes

import org.json.JSONObject

/** Semantic movement intent selected by the dialogue model. */
data class MotionIntent(
    val reply: String,
    val mood: String,
    val gaze: Float,
    val headTilt: Float,
    val bodyLean: Float,
    val gesture: String,
    val energy: Float,
    val emphasis: Float,
    val gazeVertical: Float = 0f
) {
    companion object {
        private val moods = setOf("neutral", "happy", "curious", "surprised", "angry", "sad", "sleepy", "playful")
        private val gestures = setOf("none", "talk", "open_hands", "wave", "point", "shrug", "hands_together", "present")

        private fun parseObject(raw: String): JSONObject? {
            val first = raw.indexOf('{')
            val last = raw.lastIndexOf('}')
            if (first < 0 || last <= first) return null
            return runCatching { JSONObject(raw.substring(first, last + 1)) }.getOrNull()
        }

        private fun jsonString(source: JSONObject, key: String): String? =
            source.opt(key) as? String

        private fun jsonNumber(source: JSONObject, key: String, fallback: Double): Double {
            val value = source.opt(key)
            val parsed = when (value) {
                is Number -> value.toDouble()
                is String -> value.toDoubleOrNull()
                else -> null
            } ?: return fallback
            return parsed.takeIf { it.isFinite() } ?: fallback
        }

        fun parse(raw: String, fallbackReply: String = raw): MotionIntent {
            val json = parseObject(raw)
            if (json != null) {
                val mood = jsonString(json, "mood")?.lowercase()?.takeIf { it in moods } ?: "neutral"
                val gesture = jsonString(json, "gesture")?.lowercase()?.takeIf { it in gestures } ?: "none"
                val gazeDirection = jsonString(json, "gaze")?.lowercase()
                val gaze = when (gazeDirection) {
                    "left" -> -0.65f
                    "right" -> 0.65f
                    "wander" -> 0.2f
                    else -> 0f
                }
                val gazeVertical = when (gazeDirection) {
                    "up" -> 0.35f
                    "down" -> -0.25f
                    "wander" -> 0.12f
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
                    emphasis = jsonNumber(json, "emphasis", 0.25).toFloat().coerceIn(0f, 1f),
                    gazeVertical = gazeVertical
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
