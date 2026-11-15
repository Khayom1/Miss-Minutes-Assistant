package com.khayom1.missminutes

import kotlin.random.Random

/**
 * Activity priority gates autonomous behavior. User-directed work always wins over idle motion.
 */
enum class BehaviorMode(val priority: Int) {
    IDLE(0),
    LISTENING(1),
    THINKING(2),
    SPEAKING(3),
    INTERACTING(4),
    HIDDEN(5)
}

internal enum class AutonomousAction(val animationName: String, val durationMillis: Long) {
    BLINK("Blink", 300L),
    GLANCE("Nervous_Glance", 1_200L),
    PEEK("Peek_Around", 1_800L)
}

/**
 * Schedules low-priority idle actions. It never interrupts listening, thinking, speech,
 * direct user interaction, or a hidden character. Non-idle activity postpones the next action.
 */
internal class AutonomousBehaviorEngine(
    private val random: Random = Random.Default
) {
    private var nextActionAtMillis: Long? = null
    private var lastObservedAtMillis: Long? = null

    fun nextAction(
        nowMillis: Long,
        mode: BehaviorMode,
        mood: String = "neutral",
        energy: Float = 0.35f
    ): AutonomousAction? {
        if (nowMillis < 0L) return null

        val previousTime = lastObservedAtMillis
        if (previousTime != null && nowMillis < previousTime) reset()
        lastObservedAtMillis = nowMillis

        if (mode == BehaviorMode.HIDDEN) {
            nextActionAtMillis = null
            return null
        }

        if (mode != BehaviorMode.IDLE) {
            nextActionAtMillis = nowMillis + random.nextLong(1_800L, 2_801L)
            return null
        }

        val scheduledAt = nextActionAtMillis
        if (scheduledAt == null) {
            nextActionAtMillis = nowMillis + random.nextLong(1_800L, 3_601L)
            return null
        }
        if (nowMillis < scheduledAt) return null

        nextActionAtMillis = nowMillis + random.nextLong(3_200L, 6_501L)
        return selectAction(mood, energy)
    }

    fun reset() {
        nextActionAtMillis = null
        lastObservedAtMillis = null
    }

    private fun selectAction(mood: String, energy: Float): AutonomousAction {
        if (energy.coerceIn(0f, 1f) < 0.15f) return AutonomousAction.BLINK

        val roll = random.nextInt(100)
        return when (mood.lowercase()) {
            "sleepy", "sad" -> if (roll < 82) AutonomousAction.BLINK else AutonomousAction.GLANCE
            "curious" -> when (roll) {
                in 0..54 -> AutonomousAction.GLANCE
                in 55..89 -> AutonomousAction.BLINK
                else -> AutonomousAction.PEEK
            }
            "happy", "playful" -> when (roll) {
                in 0..49 -> AutonomousAction.PEEK
                in 50..79 -> AutonomousAction.GLANCE
                else -> AutonomousAction.BLINK
            }
            else -> when (roll) {
                in 0..77 -> AutonomousAction.BLINK
                in 78..96 -> AutonomousAction.GLANCE
                else -> AutonomousAction.PEEK
            }
        }
    }
}
