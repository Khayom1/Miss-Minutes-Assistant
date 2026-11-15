package com.khayom1.missminutes

import kotlin.random.Random
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class AutonomousBehaviorEngineTest {
    @Test
    fun waitsBeforeFirstAutonomousAction() {
        val engine = AutonomousBehaviorEngine(Random(41))
        assertNull(engine.nextAction(0L, isSpeaking = false))
        assertNull(engine.nextAction(1L, isSpeaking = false))
        assertNotNull(engine.nextAction(10_000L, isSpeaking = false))
    }

    @Test
    fun speechResetsTheIdleSchedule() {
        val engine = AutonomousBehaviorEngine(Random(7))
        assertNull(engine.nextAction(0L, isSpeaking = false))
        assertNull(engine.nextAction(1_000L, isSpeaking = true))
        assertNull(engine.nextAction(2_000L, isSpeaking = false))
        assertNotNull(engine.nextAction(10_000L, isSpeaking = false))
    }

    @Test
    fun resetRestartsTheInitialDelay() {
        val engine = AutonomousBehaviorEngine(Random(13))
        engine.nextAction(0L, isSpeaking = false)
        engine.reset()
        assertNull(engine.nextAction(50_000L, isSpeaking = false))
    }

    @Test
    fun negativeClockDoesNotScheduleAnAction() {
        val engine = AutonomousBehaviorEngine(Random(3))
        assertNull(engine.nextAction(-1L, isSpeaking = false))
    }

    @Test
    fun actionDurationsArePositiveAndBounded() {
        assertEquals(listOf(300L, 1_200L, 1_800L), AutonomousAction.entries.map { it.durationMillis })
    }
}
