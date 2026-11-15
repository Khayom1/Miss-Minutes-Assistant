package com.khayom1.missminutes

import kotlin.random.Random

/**
 * Small autonomous idle scheduler. It never interrupts speech or a user-directed
 * one-shot animation; it only schedules subtle actions while the character is idle.
 */
internal enum class AutonomousAction(val animationName: String, val durationMillis: Long) {
    BLINK("Blink", 300L),
    GLANCE("Nervous_Glance", 1_200L),
    PEEK("Peek_Around", 1_800L)
}

internal class AutonomousBehaviorEngine(
    private val random: Random = Random.Default
) {
    private var nextActionAtMillis: Long? = null

    fun nextAction(
        nowMillis: Long,
        isSpeaking: Boolean,
        isInteracting: Boolean = false
    ): AutonomousAction? {
        if (nowMillis < 0L) return null

        if (isSpeaking || isInteracting) {
            nextActionAtMillis = nowMillis + random.nextLong(1_800L, 3_601L)
            return null
        }

        val scheduledAt = nextActionAtMillis
        if (scheduledAt == null) {
            nextActionAtMillis = nowMillis + random.nextLong(1_800L, 3_601L)
            return null
        }
        if (nowMillis < scheduledAt) return null

        nextActionAtMillis = nowMillis + random.nextLong(3_200L, 6_501L)
        return when (random.nextInt(100)) {
            in 0..77 -> AutonomousAction.BLINK
            in 78..96 -> AutonomousAction.GLANCE
            else -> AutonomousAction.PEEK
        }
    }

    fun reset() {
        nextActionAtMillis = null
    }
}
