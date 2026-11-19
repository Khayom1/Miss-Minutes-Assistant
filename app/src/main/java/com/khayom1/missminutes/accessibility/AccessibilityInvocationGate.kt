package com.khayom1.missminutes.accessibility

import java.util.concurrent.atomic.AtomicReference

/** Linearizes a queued main-thread action against caller timeout or cancellation. */
internal class AccessibilityInvocationGate {
    private enum class State { PENDING, RUNNING, ABANDONED, FINISHED }

    private val state = AtomicReference(State.PENDING)

    fun tryStart(): Boolean = state.compareAndSet(State.PENDING, State.RUNNING)

    /** Returns true only when execution is guaranteed not to start afterwards. */
    fun abandonIfPending(): Boolean = state.compareAndSet(State.PENDING, State.ABANDONED)

    fun finish() {
        state.compareAndSet(State.RUNNING, State.FINISHED)
    }

    fun hasStarted(): Boolean = state.get() == State.RUNNING || state.get() == State.FINISHED
}
