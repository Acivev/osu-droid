package com.osudroid.verktex.bridge

import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import org.andengine.engine.handler.runnable.RunnableHandler
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RunnableHandlerTest {

    @Test
    fun postingWhileABatchRunsDoesNotBlock() {
        val handler = RunnableHandler()
        val started = CountDownLatch(1)
        val release = CountDownLatch(1)
        val secondRan = CountDownLatch(1)

        handler.postRunnable {
            started.countDown()
            release.await(10, TimeUnit.SECONDS)
        }

        val worker = Thread { handler.onUpdate(0f) }.apply { isDaemon = true; start() }
        assertTrue(started.await(5, TimeUnit.SECONDS))

        val poster = Thread { handler.postRunnable { secondRan.countDown() } }.apply { isDaemon = true; start() }
        poster.join(1000)
        val blocked = poster.isAlive

        release.countDown()
        worker.join(5000)
        poster.join(5000)
        assertTrue("postRunnable blocked while a batch was running", !blocked)

        handler.onUpdate(0f)
        assertEquals(0L, secondRan.count)
    }

    @Test
    fun runnablesPostedDuringABatchRunOnTheNextUpdate() {
        val handler = RunnableHandler()
        val log = mutableListOf<String>()

        handler.postRunnable {
            log += "a"
            handler.postRunnable { log += "b" }
        }

        handler.onUpdate(0f)
        assertEquals(listOf("a"), log)
        handler.onUpdate(0f)
        assertEquals(listOf("a", "b"), log)
        handler.onUpdate(0f)
        assertEquals(listOf("a", "b"), log)
    }
}
