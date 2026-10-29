package com.osudroid.verktex.bridge

import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class UpdateQueueTest {

    @Test
    fun postingWhileABatchRunsDoesNotBlock() {
        val queue = UpdateQueue()
        val started = CountDownLatch(1)
        val release = CountDownLatch(1)
        val secondRan = CountDownLatch(1)

        queue.post {
            started.countDown()
            release.await(10, TimeUnit.SECONDS)
        }

        val worker = Thread { queue.drain() }.apply { isDaemon = true; start() }
        assertTrue(started.await(5, TimeUnit.SECONDS))

        val poster = Thread { queue.post { secondRan.countDown() } }.apply { isDaemon = true; start() }
        poster.join(1000)
        val blocked = poster.isAlive

        release.countDown()
        worker.join(5000)
        poster.join(5000)
        assertTrue("post blocked while a batch was running", !blocked)

        queue.drain()
        assertEquals(0L, secondRan.count)
    }

    @Test
    fun runnablesPostedDuringABatchRunOnTheNextDrain() {
        val queue = UpdateQueue()
        val log = mutableListOf<String>()

        queue.post {
            log += "a"
            queue.post { log += "b" }
        }

        queue.drain()
        assertEquals(listOf("a"), log)
        queue.drain()
        assertEquals(listOf("a", "b"), log)
        queue.drain()
        assertEquals(listOf("a", "b"), log)
    }

    @Test
    fun postsBeforeTheFirstDrainAreKept() {
        val queue = UpdateQueue()
        val log = mutableListOf<String>()

        queue.post { log += "a" }
        queue.post { log += "b" }

        queue.drain()
        assertEquals(listOf("a", "b"), log)
    }
}
