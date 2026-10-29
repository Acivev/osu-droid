package com.osudroid.verktex.bridge

import android.view.MotionEvent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class TouchControllerTest {

    private data class Call(val action: Int, val pointerId: Int, val x: Float, val y: Float, val buttonState: Int, val eventTime: Long)

    private val calls = mutableListOf<Call>()

    private fun recordingController() = TouchController { action, pointerId, x, y, buttonState, eventTime ->
        calls += Call(action, pointerId, x, y, buttonState, eventTime)
        true
    }.apply { isRunning = true }

    private fun pointers(action: Int, eventTime: Long, vararg pointers: Triple<Int, Float, Float>): MotionEvent {
        val properties = Array(pointers.size) { i ->
            MotionEvent.PointerProperties().apply {
                id = pointers[i].first
                toolType = MotionEvent.TOOL_TYPE_FINGER
            }
        }
        val coords = Array(pointers.size) { i ->
            MotionEvent.PointerCoords().apply {
                x = pointers[i].second
                y = pointers[i].third
                pressure = 1f
                size = 1f
            }
        }
        return MotionEvent.obtain(0L, eventTime, action, pointers.size, properties, coords, 0, 0, 1f, 1f, 0, 0, 0, 0)
    }

    @Test
    fun rawTableIsWrittenOnlyWhileUseRawPointer() {
        val controller = recordingController()

        controller.onTouch(MotionEvent.obtain(0L, 10L, MotionEvent.ACTION_DOWN, 5f, 6f, 0))
        assertFalse(controller.isRawPointerDown(0))
        assertEquals(0, controller.getRawPointerVersion(0))
        assertEquals(0f, controller.getRawPointerSurfaceX(0), 0f)

        controller.setUseRawPointer(true)
        controller.onTouch(MotionEvent.obtain(0L, 20L, MotionEvent.ACTION_DOWN, 7f, 8f, 0))
        assertTrue(controller.isRawPointerDown(0))
        assertEquals(7f, controller.getRawPointerSurfaceX(0), 0f)
        assertEquals(8f, controller.getRawPointerSurfaceY(0), 0f)
        assertEquals(20L, controller.getRawPointerEventTime(0))
    }

    @Test
    fun historyIsExpandedOldestFirstWithPerSampleTimeOnlyWhileEnabled() {
        val controller = recordingController()
        controller.applyTouchOptions(TouchOptions().apply {
            processHistoricalEvents = true
            useRawPointer = true
        })

        val move = MotionEvent.obtain(0L, 100L, MotionEvent.ACTION_MOVE, 1f, 1f, 1f, 1f, 0, 1f, 1f, 0, 0)
        move.addBatch(110L, 2f, 2f, 1f, 1f, 0)
        move.addBatch(120L, 3f, 3f, 1f, 1f, 0)
        assertEquals(2, move.historySize)

        controller.onTouch(move)

        // Only the current sample reaches the raw table, as one write.
        assertEquals(3f, controller.getRawPointerSurfaceX(0), 0f)
        assertEquals(120L, controller.getRawPointerEventTime(0))
        assertEquals(2, controller.getRawPointerVersion(0))

        controller.drain()
        assertEquals(
            listOf(
                Call(MotionEvent.ACTION_MOVE, 0, 1f, 1f, 0, 100L),
                Call(MotionEvent.ACTION_MOVE, 0, 2f, 2f, 0, 110L),
                Call(MotionEvent.ACTION_MOVE, 0, 3f, 3f, 0, 120L),
            ),
            calls
        )

        calls.clear()
        controller.setProcessHistoricalEvents(false)
        controller.onTouch(move)
        controller.drain()
        assertEquals(listOf(Call(MotionEvent.ACTION_MOVE, 0, 3f, 3f, 0, 120L)), calls)
    }

    @Test
    fun pointersAreWalkedInReverseIndexOrder() {
        val controller = recordingController()

        controller.onTouch(pointers(MotionEvent.ACTION_MOVE, 50L, Triple(0, 1f, 2f), Triple(1, 3f, 4f)))
        controller.drain()

        assertEquals(listOf(1, 0), calls.map { it.pointerId })
        assertEquals(listOf(3f, 1f), calls.map { it.x })
    }

    @Test
    fun cancelWritesOnlyTheEventsPointersAndFiresOnce() {
        val controller = recordingController()
        controller.setUseRawPointer(true)

        controller.onTouch(pointers(MotionEvent.ACTION_DOWN, 10L, Triple(0, 1f, 1f)))
        controller.onTouch(pointers(MotionEvent.ACTION_POINTER_DOWN or (1 shl MotionEvent.ACTION_POINTER_INDEX_SHIFT), 20L, Triple(0, 1f, 1f), Triple(1, 2f, 2f)))
        controller.onTouch(pointers(MotionEvent.ACTION_DOWN, 25L, Triple(2, 9f, 9f)))
        controller.drain()
        calls.clear()
        assertTrue(controller.isRawPointerDown(0))
        assertTrue(controller.isRawPointerDown(1))
        assertTrue(controller.isRawPointerDown(2))

        controller.onTouch(pointers(MotionEvent.ACTION_CANCEL, 30L, Triple(0, 1f, 1f), Triple(1, 2f, 2f)))

        assertFalse(controller.isRawPointerDown(0))
        assertFalse(controller.isRawPointerDown(1))
        assertEquals(30L, controller.getRawPointerEventTime(0))
        assertEquals(30L, controller.getRawPointerEventTime(1))
        // Pointer 2 is not in the cancel event and keeps its state.
        assertTrue(controller.isRawPointerDown(2))
        assertEquals(25L, controller.getRawPointerEventTime(2))

        controller.drain()
        assertEquals(listOf(Call(MotionEvent.ACTION_CANCEL, 0, 1f, 1f, 0, 30L)), calls)
    }

    @Test
    fun outsideFiresOneOutsideEvent() {
        val controller = recordingController()

        controller.onTouch(pointers(MotionEvent.ACTION_OUTSIDE, 40L, Triple(3, 5f, 6f)))
        controller.drain()

        assertEquals(listOf(Call(MotionEvent.ACTION_OUTSIDE, 3, 5f, 6f, 0, 40L)), calls)
    }

    @Test
    fun drainRunsEventsQueuedDuringTheDrain() {
        lateinit var controller: TouchController
        var queuedInside = false
        controller = TouchController { action, pointerId, x, y, buttonState, eventTime ->
            calls += Call(action, pointerId, x, y, buttonState, eventTime)
            if (!queuedInside) {
                queuedInside = true
                controller.onTouch(MotionEvent.obtain(0L, 2L, MotionEvent.ACTION_UP, 2f, 2f, 0))
            }
            true
        }
        controller.isRunning = true

        controller.onTouch(MotionEvent.obtain(0L, 1L, MotionEvent.ACTION_DOWN, 1f, 1f, 0))
        controller.drain()

        assertEquals(listOf(MotionEvent.ACTION_DOWN, MotionEvent.ACTION_UP), calls.map { it.action })

        controller.drain()
        assertEquals(2, calls.size)
    }

    @Test
    fun touchesAreDroppedWhileStopped() {
        val controller = recordingController()
        controller.isRunning = false
        controller.setUseRawPointer(true)

        assertFalse(controller.onTouch(MotionEvent.obtain(0L, 1L, MotionEvent.ACTION_DOWN, 1f, 1f, 0)))
        controller.drain()

        assertTrue(calls.isEmpty())
        assertFalse(controller.isRawPointerDown(0))

        controller.isRunning = true
        assertTrue(controller.onTouch(MotionEvent.obtain(0L, 1L, MotionEvent.ACTION_DOWN, 1f, 1f, 0)))
    }

    @Test
    fun versionIsEvenAfterAWrite() {
        val controller = recordingController()
        controller.setUseRawPointer(true)

        controller.onTouch(MotionEvent.obtain(0L, 1L, MotionEvent.ACTION_DOWN, 1f, 1f, 0))
        val version = controller.getRawPointerVersion(0)
        assertEquals(2, version)
        assertEquals(0, version and 1)

        // clearRawPointers keeps counting, resetRawPointers hard-resets the counter.
        controller.clearRawPointers()
        assertEquals(4, controller.getRawPointerVersion(0))
        controller.resetRawPointers()
        assertEquals(0, controller.getRawPointerVersion(0))
        assertEquals(-1, controller.getRawPointerVersion(controller.getRawPointerCapacity()))
    }
}
