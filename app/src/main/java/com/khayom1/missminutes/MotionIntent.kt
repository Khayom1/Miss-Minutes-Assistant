package com.khayom1.missminutes

import org.json.JSONObject

/**
 * High-level intent selected by the dialogue model. The runtime deliberately maps
 * intent to clips that are present in the current bundled GLB; it never invents
 * bone names or writes transforms for controls the asset does not contain.
 */
data class MotionIntent(
    val reply: String,
    val animation: String,
    val emotion: String
) {
    companion object {
        val allowedAnimations = setOf(
            "Idle_Breathe", "Blink", "Wave_Greeting", "Pointing", "Talk_Loop",
            "Laugh", "Surprised", "Angry_Expression", "Sleep_Idle",
            "Glitch_Recovery", "Walk_Cycle", "Run_Cycle", "Look_Around",
            "Peek_Around", "Spin_In_Place", "Nervous_Glance", "Cheerful_Bounce"
        )

        fun parse(raw: String, fallbackReply: String = raw): MotionIntent {
            val jsonStart = raw.indexOf('{')
            val jsonEnd = raw.lastIndexOf('}')
            if (jsonStart >= 0 && jsonEnd > jsonStart) {
                runCatching {
                    val json = JSONObject(raw.substring(jsonStart, jsonEnd + 1))
                    val reply = json.optString("reply").trim().ifBlank { fallbackReply }
                    val requested = json.optString("animation", "Idle_Breathe")
                    val animation = requested.takeIf { it in allowedAnimations } ?: infer(reply)
                    val emotion = json.optString("emotion", "neutral").take(32)
                    return MotionIntent(reply, animation, emotion)
                }
            }
            return MotionIntent(fallbackReply, infer(fallbackReply), "neutral")
        }

        fun infer(text: String): String {
            val value = text.lowercase()
            return when {
                listOf("хаха", "хи-хи", "смеш", "laugh", "funny", "хох", "😂").any(value::contains) -> "Laugh"
                listOf("удив", "ого", "невероят", "surpris", "wow", "what?!").any(value::contains) -> "Surprised"
                listOf("злюсь", "сердит", "angry", "раздраж").any(value::contains) -> "Angry_Expression"
                listOf("привет", "здравств", "hello", "hi there", "добро пожал").any(value::contains) -> "Wave_Greeting"
                listOf("смотри", "вот", "обрати внимание", "look at", "point").any(value::contains) -> "Pointing"
                listOf("пока", "до встречи", "bye", "goodbye").any(value::contains) -> "Wave_Greeting"
                else -> "Idle_Breathe"
            }
        }
    }
}
