package com.khayom1.missminutes.communication.sms

import java.util.concurrent.CountDownLatch
import java.util.concurrent.RejectedExecutionException
import java.util.concurrent.TimeUnit
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class IncomingSmsQueueTest {
    @Test
    fun queueRejectsWorkAfterItsBoundedCapacityIsReached() {
        val executor = SmsProcessingQueue.create()
        val workerStarted = CountDownLatch(1)
        val releaseWorker = CountDownLatch(1)
        try {
            executor.execute {
                workerStarted.countDown()
                try {
                    releaseWorker.await()
                } catch (_: InterruptedException) {
                    Thread.currentThread().interrupt()
                }
            }
            assertTrue(workerStarted.await(2, TimeUnit.SECONDS))

            repeat(SmsProcessingQueue.MAX_QUEUED_MESSAGES) {
                executor.execute {}
            }
            assertEquals(SmsProcessingQueue.MAX_QUEUED_MESSAGES, executor.queue.size)

            var rejected = false
            try {
                executor.execute {}
            } catch (_: RejectedExecutionException) {
                rejected = true
            }
            assertTrue("The SMS queue must reject overflow instead of growing without bounds.", rejected)
        } finally {
            releaseWorker.countDown()
            executor.shutdownNow()
        }
    }
}
