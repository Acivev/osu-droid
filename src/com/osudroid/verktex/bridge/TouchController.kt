package com.osudroid.verktex.bridge

import android.view.MotionEvent
import java.util.ArrayDeque
import java.util.concurrent.atomic.AtomicIntegerArray
import java.util.concurrent.atomic.AtomicLongArray

/**
 * Takes surface touches on the UI thread and hands them to [sink] on the update thread.
 *
 * [onTouch] walks the [MotionEvent] and queues one event per pointer (and per historical sample while
 * [isProcessHistoricalEvents]); [drain] runs them through [sink] in order. While [isUseRawPointer], [onTouch] also
 * writes each pointer's latest state to the raw pointer table, which the update thread reads without waiting for
 * [drain].
 *
 * Every raw table write is a seqlock write: the pointer's version turns odd, the fields are stored, the version turns
 * even again. For a coherent snapshot of several fields, read [getRawPointerVersion] before and after the fields and
 * retry while it is odd or changed. The single-field getters on their own give no cross-field coherence.
 */
class TouchController(
    private val sink: (action: Int, pointerId: Int, x: Float, y: Float, buttonState: Int, eventTime: Long) -> Boolean
) {

    /**
     * While false, [onTouch] drops touches and returns false.
     */
    @Volatile
    var isRunning = false

    // Volatile so that a change from applyTouchOptions() on the update thread is seen by onTouch() on the UI thread.
    @Volatile
    private var useRawPointer = false

    @Volatile
    private var processHistoricalEvents = false

    // Atomic arrays give each element volatile store/load semantics, which gives the seqlock its store-store and
    // load-load ordering. Floats are stored as their raw int bits.
    private val rawPointerX = AtomicIntegerArray(RAW_POINTER_CAPACITY)
    private val rawPointerY = AtomicIntegerArray(RAW_POINTER_CAPACITY)
    private val rawPointerDown = AtomicIntegerArray(RAW_POINTER_CAPACITY)
    private val rawPointerEventTime = AtomicLongArray(RAW_POINTER_CAPACITY)
    private val rawPointerVersion = AtomicIntegerArray(RAW_POINTER_CAPACITY)

    // Guarded by itself, together with freeEvents.
    private val queuedEvents = ArrayDeque<Event>()
    private val freeEvents = ArrayDeque<Event>()


    /**
     * Handles a surface touch on the UI thread. Returns false and queues nothing while not [isRunning].
     */
    fun onTouch(event: MotionEvent): Boolean {
        if (!isRunning) {
            return false
        }

        val action = event.action and MotionEvent.ACTION_MASK

        when (action) {
            MotionEvent.ACTION_DOWN, MotionEvent.ACTION_POINTER_DOWN -> onTouchAction(MotionEvent.ACTION_DOWN, event)
            MotionEvent.ACTION_UP, MotionEvent.ACTION_POINTER_UP -> onTouchAction(MotionEvent.ACTION_UP, event)

            MotionEvent.ACTION_CANCEL, MotionEvent.ACTION_OUTSIDE -> {
                // A cancel aborts every pointer at once, so none of them may stay down in the raw table.
                for (i in 0 until event.pointerCount) {
                    updateRawPointer(event.getPointerId(i), 0f, 0f, false, event.eventTime)
                }
                onTouchAction(action, event)
            }

            MotionEvent.ACTION_MOVE -> onTouchMove(event)
            else -> throw IllegalArgumentException("Invalid Action detected: $action")
        }

        return true
    }

    private fun onTouchMove(event: MotionEvent) {
        // Batched positions first and oldest first, each with its own time, for accurate slider tracking. They are
        // not written to the raw table, which only holds the latest position.
        // See https://developer.android.com/reference/android/view/MotionEvent#batching
        if (processHistoricalEvents) {
            for (h in 0 until event.historySize) {
                val historicalEventTime = event.getHistoricalEventTime(h)

                for (i in event.pointerCount - 1 downTo 0) {
                    queue(MotionEvent.ACTION_MOVE, event.getPointerId(i), event.getHistoricalX(i, h), event.getHistoricalY(i, h), 0, historicalEventTime)
                }
            }
        }

        for (i in event.pointerCount - 1 downTo 0) {
            val pointerId = event.getPointerId(i)
            val x = event.getX(i)
            val y = event.getY(i)

            updateRawPointer(pointerId, x, y, true, event.eventTime)
            queue(MotionEvent.ACTION_MOVE, pointerId, x, y, event.buttonState, event.eventTime)
        }
    }

    private fun onTouchAction(action: Int, event: MotionEvent) {
        val pointerIndex = (event.action and MotionEvent.ACTION_POINTER_INDEX_MASK) shr MotionEvent.ACTION_POINTER_INDEX_SHIFT
        val pointerId = event.getPointerId(pointerIndex)
        val x = event.getX(pointerIndex)
        val y = event.getY(pointerIndex)

        updateRawPointer(pointerId, x, y, action == MotionEvent.ACTION_DOWN, event.eventTime)
        queue(action, pointerId, x, y, event.buttonState, event.eventTime)
    }


    private fun queue(action: Int, pointerId: Int, x: Float, y: Float, buttonState: Int, eventTime: Long) {
        synchronized(queuedEvents) {
            val event = freeEvents.pollFirst() ?: Event()
            event.action = action
            event.pointerId = pointerId
            event.x = x
            event.y = y
            event.buttonState = buttonState
            event.eventTime = eventTime
            queuedEvents.addLast(event)
        }
    }

    /**
     * Runs the queued touches through the sink on the update thread until the queue is empty, including touches
     * queued while it runs. The lock is not held while the sink runs, so [onTouch] never waits on it.
     */
    fun drain() {
        while (true) {
            val event = synchronized(queuedEvents) { queuedEvents.pollFirst() } ?: return

            sink(event.action, event.pointerId, event.x, event.y, event.buttonState, event.eventTime)

            synchronized(queuedEvents) { freeEvents.addLast(event) }
        }
    }


    fun applyTouchOptions(options: TouchOptions) {
        setUseRawPointer(options.useRawPointer)
        setProcessHistoricalEvents(options.processHistoricalEvents)
    }

    fun isUseRawPointer() = useRawPointer

    fun setUseRawPointer(useRawPointer: Boolean) {
        this.useRawPointer = useRawPointer
    }

    fun isProcessHistoricalEvents() = processHistoricalEvents

    fun setProcessHistoricalEvents(processHistoricalEvents: Boolean) {
        this.processHistoricalEvents = processHistoricalEvents
    }


    fun getRawPointerVersion(pointerId: Int) =
        if (pointerId < 0 || pointerId >= RAW_POINTER_CAPACITY) -1 else rawPointerVersion.get(pointerId)

    fun isRawPointerDown(pointerId: Int) =
        pointerId >= 0 && pointerId < RAW_POINTER_CAPACITY && rawPointerDown.get(pointerId) != 0

    fun getRawPointerSurfaceX(pointerId: Int) =
        if (pointerId < 0 || pointerId >= RAW_POINTER_CAPACITY) 0f else Float.fromBits(rawPointerX.get(pointerId))

    fun getRawPointerSurfaceY(pointerId: Int) =
        if (pointerId < 0 || pointerId >= RAW_POINTER_CAPACITY) 0f else Float.fromBits(rawPointerY.get(pointerId))

    fun getRawPointerEventTime(pointerId: Int) =
        if (pointerId < 0 || pointerId >= RAW_POINTER_CAPACITY) 0L else rawPointerEventTime.get(pointerId)

    fun getRawPointerCapacity() = RAW_POINTER_CAPACITY

    /**
     * Clears every raw pointer through a normal seqlock write, so the versions keep counting. Does nothing while
     * raw pointer tracking is off, to spare the atomic increments.
     */
    fun clearRawPointers() {
        if (useRawPointer) {
            for (i in 0 until RAW_POINTER_CAPACITY) {
                updateRawPointer(i, 0f, 0f, false, 0L)
            }
        }
    }

    /**
     * Hard-resets every raw pointer, versions included. Runs whether or not raw pointer tracking is on, so it wipes
     * stale state when switching modes (e.g. on map start).
     */
    fun resetRawPointers() {
        for (i in 0 until RAW_POINTER_CAPACITY) {
            rawPointerVersion.set(i, 0)
            rawPointerX.set(i, 0)
            rawPointerY.set(i, 0)
            rawPointerDown.set(i, 0)
            rawPointerEventTime.set(i, 0L)
        }
    }

    private fun updateRawPointer(id: Int, x: Float, y: Float, down: Boolean, eventTime: Long) {
        if (!useRawPointer || id < 0 || id >= RAW_POINTER_CAPACITY) {
            return
        }

        // Odd while the write is in progress, even again once it is done.
        rawPointerVersion.incrementAndGet(id)
        rawPointerX.set(id, x.toRawBits())
        rawPointerY.set(id, y.toRawBits())
        rawPointerDown.set(id, if (down) 1 else 0)
        rawPointerEventTime.set(id, eventTime)
        rawPointerVersion.incrementAndGet(id)
    }


    private class Event {
        var action = 0
        var pointerId = 0
        var x = 0f
        var y = 0f
        var buttonState = 0
        var eventTime = 0L
    }

    companion object {
        private const val RAW_POINTER_CAPACITY = 10
    }
}
