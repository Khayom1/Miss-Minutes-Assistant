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
        assertNull(engine.nextAction(0L, BehaviorMode.IDLE))
        assertNull(engine.nextAction(1L, BehaviorMode.IDLE))
        assertNotNull(engine.nextAction(10_000L, BehaviorMode.IDLE))
    }

    @Test
    fun speechResetsTheIdleSchedule() {
        val engine = AutonomousBehaviorEngine(Random(7))
        assertNull(engine.nextAction(0L, BehaviorMode.IDLE))
        assertNull(engine.nextAction(1_000L, BehaviorMode.SPEAKING))
        assertNull(engine.nextAction(2_000L, BehaviorMode.IDLE))
        assertNotNull(engine.nextAction(10_000L, BehaviorMode.IDLE))
    }

    @Test
    fun thinkingAndListeningSuppressAutonomousActions() {
        val engine = AutonomousBehaviorEngine(Random(11))
        assertNull(engine.nextAction(0L, BehaviorMode.IDLE))
        assertNull(engine.nextAction(5_000L, BehaviorMode.THINKING))
        assertNull(engine.nextAction(6_000L, BehaviorMode.LISTENING))
        assertNull(engine.nextAction(7_000L, BehaviorMode.INTERACTING))
        assertNull(engine.nextAction(7_500L, BehaviorMode.IDLE))
        assertNotNull(engine.nextAction(20_000L, BehaviorMode.IDLE))
    }

    @Test
    fun hiddenModeClearsPendingIdleAction() {
        val engine = AutonomousBehaviorEngine(Random(13))
        assertNull(engine.nextAction(0L, BehaviorMode.IDLE))
        assertNull(engine.nextAction(1_000L, BehaviorMode.HIDDEN))
        assertNull(engine.nextAction(50_000L, BehaviorMode.IDLE))
    }

    @Test
    fun lowEnergySelectsOnlyBlink() {
        val engine = AutonomousBehaviorEngine(Random(3))
        assertNull(engine.nextAction(0L, BehaviorMode.IDLE, energy = 0.05f))
        assertEquals(AutonomousAction.BLINK, engine.nextAction(10_000L, BehaviorMode.IDLE, energy = 0.05f))
    }

    @Test
    fun moodBiasesTheAutonomousAction() {
        val curious = AutonomousBehaviorEngine(Random(21))
        curious.nextAction(0L, BehaviorMode.IDLE)
        assertNotNull(curious.nextAction(10_000L, BehaviorMode.IDLE, mood = "curious", energy = 0.8f))

        val sleepy = AutonomousBehaviorEngine(Random(21))
        sleepy.nextAction(0L, BehaviorMode.IDLE)
        assertNotNull(sleepy.nextAction(10_000L, BehaviorMode.IDLE, mood = "sleepy", energy = 0.8f))
    }

    @Test
    fun resetRestartsTheInitialDelay() {
        val engine = AutonomousBehaviorEngine(Random(13))
        engine.nextAction(0L, BehaviorMode.IDLE)
        engine.reset()
        assertNull(engine.nextAction(50_000L, BehaviorMode.IDLE))
    }

    @Test
    fun negativeClockDoesNotScheduleAnAction() {
        val engine = AutonomousBehaviorEngine(Random(3))
        assertNull(engine.nextAction(-1L, BehaviorMode.IDLE))
    }

    @Test
    fun actionDurationsArePositiveAndBounded() {
        assertEquals(listOf(300L, 1_200L, 1_800L), AutonomousAction.entries.map { it.durationMillis })
    }

    @Test
    fun behaviorModePriorityIsExplicit() {
        assertEquals(true, BehaviorMode.SPEAKING.priority > BehaviorMode.THINKING.priority)
        assertEquals(true, BehaviorMode.INTERACTING.priority > BehaviorMode.SPEAKING.priority)
        assertEquals(true, BehaviorMode.HIDDEN.priority > BehaviorMode.INTERACTING.priority)
    }
}
