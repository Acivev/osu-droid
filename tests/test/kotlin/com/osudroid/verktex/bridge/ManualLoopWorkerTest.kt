package com.osudroid.verktex.bridge

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ManualLoopWorkerTest {

    @Test
    fun pumpBeforeStartDoesNothing() {
        var n = 0
        val w = ManualLoopWorker("w") { n++ }
        w.pump()
        assertEquals(0, n)
    }

    @Test
    fun pumpAfterStartRunsLoopOnce() {
        var n = 0
        val w = ManualLoopWorker("w") { n++ }
        w.start()
        w.pump()
        w.pump()
        assertEquals(2, n)
    }

    @Test
    fun scheduledTasksRunInOrderBeforeLoop() {
        val log = mutableListOf<String>()
        val w = ManualLoopWorker("w") { log += "loop" }
        w.start()
        val a = w.schedule { log += "a" }
        val b = w.schedule { log += "b" }
        w.pump()
        assertEquals(listOf("a", "b", "loop"), log)
        assertTrue(a.isCompleted && b.isCompleted)
    }

    @Test
    fun pauseSuppressesPumpAndResumeRestoresIt() {
        var n = 0
        val w = ManualLoopWorker("w") { n++ }
        w.start()
        w.pause()
        w.pump()
        assertEquals(0, n)
        w.resume()
        w.pump()
        assertEquals(1, n)
    }

    @Test
    fun stopEndsRunning() {
        var n = 0
        val w = ManualLoopWorker("w") { n++ }
        w.start()
        w.stop()
        assertFalse(w.isRunning())
        w.pump()
        assertEquals(0, n)
    }

    @Test
    fun checkPassesOnPumpingThreadAndFailsElsewhere() {
        val w = ManualLoopWorker("w") {}
        w.start()
        w.pump()
        w.check()
        var thrown: Throwable? = null
        val t = Thread {
            try {
                w.check()
            } catch (e: IllegalStateException) {
                thrown = e
            }
        }
        t.start()
        t.join()
        assertNotNull(thrown)
    }

    @Test
    fun isLoopingIsTrueInsideLoop() {
        var inside = false
        lateinit var w: ManualLoopWorker
        w = ManualLoopWorker("w") { inside = w.isLooping() }
        w.start()
        w.pump()
        assertTrue(inside)
        assertFalse(w.isLooping())
    }
}
