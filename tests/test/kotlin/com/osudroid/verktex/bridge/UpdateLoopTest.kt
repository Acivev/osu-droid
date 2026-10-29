package com.osudroid.verktex.bridge

import java.util.Collections
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

// Robolectric only for android.os.Process.setThreadPriority on the update thread.
@RunWith(RobolectricTestRunner::class)
class UpdateLoopTest {

    // Stands in for the GL thread; every draw is bounded by draw()'s timeout.
    private val glThread: ExecutorService = Executors.newSingleThreadExecutor { Thread(it, "TestGLThread").apply { isDaemon = true } }

    private val loops = mutableListOf<UpdateLoop>()

    @After
    fun tearDown() {
        loops.forEach { loop -> glThread.submit { loop.joinThread() }.get(5, TimeUnit.SECONDS) }
        glThread.shutdownNow()
    }

    private fun loop(onTick: () -> Unit) = UpdateLoop(onTick = onTick).also {
        it.thread.isDaemon = true
        loops += it
    }

    private fun draw(loop: UpdateLoop, block: () -> Unit = {}) {
        glThread.submit { loop.onDrawFrame(block) }.get(5, TimeUnit.SECONDS)
    }

    @Test
    fun updateAndDrawAlternateStrictly() {
        val log = Collections.synchronizedList(mutableListOf<String>())
        val loop = loop { log += "U" }

        // Started before the thread runs, so no stopped-branch handshake lets a draw in before the first tick.
        loop.start()
        loop.startThread()

        repeat(20) { draw(loop) { log += "D" } }

        val snapshot = synchronized(log) { log.toList() }
        assertEquals(20, snapshot.count { it == "D" })
        snapshot.forEachIndexed { i, entry -> assertEquals("entry $i of $snapshot", if (i % 2 == 0) "U" else "D", entry) }
    }

    @Test
    fun stoppedLoopKeepsHandshaking() {
        val ticks = AtomicInteger()
        val loop = loop { ticks.incrementAndGet() }

        loop.startThread()
        loop.start()
        draw(loop)

        loop.stop()
        assertFalse(loop.isRunning)
        // Consumes a tick that read isRunning before stop().
        draw(loop)
        val ticksAfterStop = ticks.get()

        val before = System.nanoTime()
        draw(loop)
        val elapsedMs = (System.nanoTime() - before) / 1_000_000
        assertTrue("stopped draw took $elapsedMs ms", elapsedMs < 100)

        repeat(5) { draw(loop) }
        assertEquals(ticksAfterStop, ticks.get())
    }

    @Test
    fun postsWaitWhileStopped() {
        lateinit var loop: UpdateLoop
        loop = loop { loop.queue.drain() }
        val ran = AtomicBoolean()

        loop.startThread()
        loop.post { ran.set(true) }

        repeat(5) { draw(loop) }
        assertFalse(ran.get())

        loop.start()
        repeat(3) { draw(loop) }
        assertTrue(ran.get())
    }

    @Test
    fun joinEndsTheThread() {
        val running = loop {}
        running.start()
        running.startThread()
        repeat(3) { draw(running) }
        glThread.submit { running.joinThread() }.get(5, TimeUnit.SECONDS)
        assertFalse(running.thread.isAlive)

        // A draw after the join returns without waiting for a tick that never comes, and draws nothing.
        val drew = AtomicBoolean()
        draw(running) { drew.set(true) }
        assertFalse(drew.get())

        val stopped = loop {}
        stopped.startThread()
        repeat(3) { draw(stopped) }
        glThread.submit { stopped.joinThread() }.get(5, TimeUnit.SECONDS)
        assertFalse(stopped.thread.isAlive)
    }
}
