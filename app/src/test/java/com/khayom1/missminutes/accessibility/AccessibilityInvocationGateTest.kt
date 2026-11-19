package com.khayom1.missminutes.accessibility

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AccessibilityInvocationGateTest {
    @Test
    fun timeoutBeforeDispatchPreventsQueuedActionFromStarting() {
        val gate = AccessibilityInvocationGate()

        assertTrue(gate.abandonIfPending())
        assertFalse(gate.tryStart())
    }

    @Test
    fun timeoutCannotPretendAnAlreadyStartedActionWasCancelled() {
        val gate = AccessibilityInvocationGate()

        assertTrue(gate.tryStart())
        assertFalse(gate.abandonIfPending())
        gate.finish()
        assertTrue(gate.hasStarted())
    }

    @Test
    fun completedActionCannotBeReclassifiedAsNeverStarted() {
        val gate = AccessibilityInvocationGate()

        assertTrue(gate.tryStart())
        gate.finish()

        assertFalse(gate.abandonIfPending())
        assertTrue(gate.hasStarted())
    }
}
