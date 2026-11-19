package com.khayom1.missminutes.assistant

/**
 * Owns one asynchronous recognizer resource at a time.
 *
 * The monotonically increasing session id rejects callbacks delivered after
 * cancellation/replacement. Resource identity is checked as well, so an old
 * completion cannot release a newer recognizer.
 */
internal class RecognitionSession<T : Any> {
    private var generation = 0L
    private var activeId: Long? = null
    private var activeResource: T? = null

    @Synchronized
    fun begin(resource: T): Long {
        generation += 1
        activeId = generation
        activeResource = resource
        return generation
    }

    @Synchronized
    fun isCurrent(sessionId: Long, resource: T): Boolean =
        activeId == sessionId && activeResource === resource

    @Synchronized
    fun finish(sessionId: Long, resource: T): Boolean {
        if (!isCurrent(sessionId, resource)) return false
        activeId = null
        activeResource = null
        return true
    }

    @Synchronized
    fun cancel(): T? {
        generation += 1
        activeId = null
        return activeResource.also { activeResource = null }
    }

    @Synchronized
    fun current(): T? = activeResource
}
