package com.osudroid.verktex.bridge

import android.os.Process
import android.util.Log
import com.rian.andengine.timing.ThrottledFrameClock
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.locks.ReentrantLock
import ru.nsu.ccfit.zuev.osuplus.BuildConfig

/**
 * The update thread and its lock-step handshake with the GL thread, ported from AndEngine's `Engine`, `UpdateThread`
 * and `EngineLock`.
 *
 * Each frame the update side runs [onTick], signals "can draw" and waits for "can update"; the GL side ([onDrawFrame])
 * waits for "can draw", draws and signals "can update". Both run under the loop's own frame lock, so one tick and one
 * draw strictly alternate. Ordering this lock before the Verktex tree lock is the callers' business.
 *
 * While stopped the update side keeps the handshake going without ticking, sleeping 16 ms between rounds, so the GL
 * thread is never left waiting.
 *
 * [onTick] runs under the frame lock and is the whole frame: the loop does not drain [queue] itself. The activity
 * composes it, in this order: `clock.processFrame()`, the touch drain, `queue.drain()`, `VerktexBridge.pumpUpdate()`.
 * Posts made while stopped therefore wait until [start].
 *
 * AndEngine's `mIsLagging` (a forced 16.6 ms delta after a frame that uploaded buffers or textures) has no
 * equivalent: Verktex uploads inside the draw, and [clock] measures the real frame time.
 */
class UpdateLoop(val clock: ThrottledFrameClock = ThrottledFrameClock(), private val onTick: () -> Unit) {

    val queue = UpdateQueue()

    private val lock = ReentrantLock()
    private val drawingCondition = lock.newCondition()
    private val drawing = AtomicBoolean(false)

    // Guarded by lock.
    private var destroyed = false

    private var running = false

    val isRunning: Boolean
        @Synchronized get() = running

    val thread: Thread

    init {
        thread = Thread(::run, THREAD_NAME)
    }

    fun startThread() {
        thread.start()
    }

    @Synchronized
    fun start() {
        if (!running) {
            running = true
        }
    }

    @Synchronized
    fun stop() {
        if (running) {
            running = false
        }
    }

    fun post(runnable: Runnable) {
        queue.post(runnable)
    }

    /**
     * Draws one frame on the GL thread once the update side has ticked. Returns without drawing once [joinThread] has
     * run, so the GL thread does not wait for a tick that never comes.
     */
    fun onDrawFrame(draw: () -> Unit) {
        lock.lock()
        try {
            waitUntilCanDraw()

            if (destroyed) {
                return
            }

            draw()

            notifyCanUpdate()
        } finally {
            lock.unlock()
        }
    }

    /**
     * Ends the update thread: marks the loop destroyed, wakes both waiting sides, interrupts the thread (ending a
     * stopped round's sleep) and joins it.
     */
    fun joinThread() {
        lock.lock()
        try {
            destroyed = true
            notifyCanUpdate()
        } finally {
            lock.unlock()
        }

        if (Thread.currentThread() === thread) {
            return
        }

        thread.interrupt()
        try {
            thread.join()
        } catch (e: InterruptedException) {
            Log.e(TAG, "Could not join UpdateThread.", e)
            Thread.currentThread().interrupt()
        }
    }

    private fun run() {
        Process.setThreadPriority(Process.THREAD_PRIORITY_DEFAULT)
        try {
            while (onTickUpdate()) {
                // Ticks until destroyed.
            }
        } catch (e: InterruptedException) {
            if (BuildConfig.DEBUG) {
                Log.d(TAG, "UpdateThread interrupted. Don't worry - this InterruptedException is most likely expected!", e)
            }
            Thread.currentThread().interrupt()
        }
    }

    /**
     * One round of the update side. Returns `false` once destroyed, where AndEngine threw `EngineDestroyedException`.
     */
    private fun onTickUpdate(): Boolean {
        if (isRunning) {
            lock.lock()
            try {
                if (destroyed) {
                    return false
                }

                onTick()

                if (destroyed) {
                    return false
                }

                notifyCanDraw()
                waitUntilCanUpdate()
            } finally {
                lock.unlock()
            }
        } else {
            lock.lock()
            try {
                if (destroyed) {
                    return false
                }

                notifyCanDraw()
                waitUntilCanUpdate()
            } finally {
                lock.unlock()
            }

            Thread.sleep(16)
        }

        return true
    }

    private fun notifyCanDraw() {
        drawing.set(true)
        drawingCondition.signalAll()
    }

    private fun notifyCanUpdate() {
        drawing.set(false)
        drawingCondition.signalAll()
    }

    private fun waitUntilCanDraw() {
        while (!drawing.get() && !destroyed) {
            drawingCondition.await()
        }
    }

    private fun waitUntilCanUpdate() {
        while (drawing.get()) {
            drawingCondition.await()
        }
    }

    private companion object {
        const val THREAD_NAME = "UpdateThread"
        const val TAG = "UpdateLoop"
    }
}
