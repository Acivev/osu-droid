package com.osudroid.verktex.bridge

import com.reco1l.verktex.worker.LoopWorker
import java.util.concurrent.ConcurrentLinkedQueue

/**
 * A [LoopWorker] that owns no thread. The host advances it by calling [pump] from a thread it
 * already controls (such as AndEngine's GL or update thread), which runs the scheduled tasks and
 * then one iteration of the loop.
 */
class ManualLoopWorker(override val name: String, private val loop: () -> Unit) : LoopWorker {

    @Volatile private var started = false
    @Volatile private var paused = false
    @Volatile private var stopped = false
    @Volatile private var looping = false

    /**
     * The thread [check] accepts, set by [bindThread] or by the last [pump].
     */
    @Volatile var pumpingThread: Thread? = null
        private set

    val isCurrentThread: Boolean
        get() = pumpingThread == Thread.currentThread()

    private val taskQueue = ConcurrentLinkedQueue<LoopWorker.TaskState>()

    fun pump() {
        if (!started || paused || stopped) {
            return
        }

        pumpingThread = Thread.currentThread()
        looping = true

        try {
            while (!stopped) {
                val task = taskQueue.poll() ?: break
                task.task()
                task.isCompleted = true
            }

            if (!stopped) {
                onLoop()
            }
        } finally {
            looping = false
        }
    }

    /**
     * Sets the thread [check] accepts before the first [pump], for hosts that know it in advance.
     */
    fun bindThread(thread: Thread) {
        pumpingThread = thread
    }

    override fun start() {
        started = true
    }

    override fun pause() {
        paused = true
    }

    override fun resume() {
        paused = false
    }

    override fun stop() {
        stopped = true
    }

    override fun check() {
        if (!isCurrentThread) {
            throw IllegalStateException("This method must be called from the $name thread.")
        }
    }

    override fun isRunning() = started && !stopped

    override fun isLooping() = looping

    override fun schedule(task: () -> Unit): LoopWorker.TaskState {
        val taskState = LoopWorker.TaskState(task)
        taskQueue.add(taskState)
        return taskState
    }

    override fun onLoop() = loop()
}
