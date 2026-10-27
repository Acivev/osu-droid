package com.osudroid.verktex.results

import com.osudroid.verktex.VerktexTestFixtures
import com.reco1l.osu.ui.entity.ScrollableList
import com.reco1l.verktex.data.LayoutConstraints
import com.reco1l.verktex.data.Vec4
import com.reco1l.verktex.data.fill
import com.reco1l.verktex.data.px
import com.reco1l.verktex.input.InputEvent
import com.reco1l.verktex.input.PointerEvent
import com.reco1l.verktex.time.TickingClock
import com.reco1l.verktex.ui.shape.UIBox
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.After
import org.junit.Before
import org.junit.Test
import ru.nsu.ccfit.zuev.osu.Config
import kotlin.math.hypot

/**
 * The numbers follow ScrollableList.kt before the port, with RES_HEIGHT 720 and the fixtures' 720 px
 * high window (LegacyUnits.scale = 1): three 100-high items from camY -146 end at y = 461, so
 * maxY = 461 - 146 - 0.8 * (720 - 110 - (100 - 32)) = -118.6.
 */
@Suppress("DEPRECATION")
class ScrollableListTest {

    private class Clock(override val elapsedTickTime: Float) : TickingClock() {
        override val lastTickTime = 0f
        override val time = 0f
        override val isRunning = true
    }

    private class TestList : ScrollableList() {
        var onItemTap = 0

        val cameraY get() = camY

        var velocity
            get() = velocityY
            set(value) { velocityY = value }

        init {
            itemHeight = 100f
            repeat(3) { this += TestItem() }
        }

        // The tap rule of StatisticSelector.BoardItem.
        inner class TestItem : UIBox() {
            private var moved = false
            private var dx = 0f
            private var dy = 0f

            init {
                width = fill()
                height = 100f.px
            }

            override fun onInputEvent(event: InputEvent): Boolean {
                if (event !is PointerEvent) return false

                val local = toLocalSpaceFromScene(event.x, event.y)

                if (event.action == PointerEvent.Action.Down) {
                    moved = false
                    dx = local.x
                    dy = local.y
                    return true
                }

                if (event.action == PointerEvent.Action.Up) {
                    velocityY = 0f

                    if (moved || isScroll) return false

                    onItemTap++
                }

                if (event.action == PointerEvent.Action.Move && hypot(dx - local.x, dy - local.y) > 10) {
                    moved = true
                }

                return false
            }
        }
    }

    private val list = TestList()

    private var resWidth = 0
    private var resHeight = 0

    @After fun tearDown() {
        Config.setRES_WIDTH(resWidth)
        Config.setRES_HEIGHT(resHeight)
    }

    @Before fun setUp() {
        VerktexTestFixtures.ensureInitialized()
        resWidth = Config.getRES_WIDTH()
        resHeight = Config.getRES_HEIGHT()
        Config.setRES_WIDTH(1280)
        Config.setRES_HEIGHT(720)
        tick(0f)
    }

    private fun tick(seconds: Float) {
        list.update(Clock(seconds))
        list.measure(LayoutConstraints(minWidth = 0f, maxWidth = 1280f, minHeight = 0f, maxHeight = 720f))
        list.layout(Vec4(0f, 0f, 1280f, 720f))
    }

    private fun pointer(action: PointerEvent.Action, x: Float, y: Float) =
        list.onInputEvent(PointerEvent(action = action, pointerId = 0, x = x, y = y))

    @Test fun dragMovesTheCameraByTheDragDistance() {
        // The first move past 10 px only starts the scroll, the next one moves the camera.
        pointer(PointerEvent.Action.Down, 600f, 200f)
        pointer(PointerEvent.Action.Move, 600f, 180f)
        assertEquals(-146f, list.cameraY, 1e-4f)

        pointer(PointerEvent.Action.Move, 600f, 160f)
        assertEquals(-126f, list.cameraY, 1e-4f)
    }

    @Test fun flingDecaysByTenPerSecondAndStopsUnderTheThreshold() {
        list.velocity = 100f
        tick(0.1f)
        assertEquals(99f, list.velocity, 1e-4f)

        list.velocity = 40f
        tick(0.1f)
        assertEquals(0f, list.velocity, 0f)
        assertFalse(list.isScroll)
    }

    @Test fun cameraIsClampedToMaxY() {
        pointer(PointerEvent.Action.Down, 600f, 200f)
        pointer(PointerEvent.Action.Move, 600f, 180f)
        pointer(PointerEvent.Action.Move, 600f, -2000f)
        assertEquals(-118.6f, list.cameraY, 1e-3f)

        pointer(PointerEvent.Action.Move, 600f, 2000f)
        assertEquals(-146f, list.cameraY, 1e-4f)
    }

    @Test fun tapWithoutMoveSelectsOnce() {
        pointer(PointerEvent.Action.Down, 600f, 200f)
        pointer(PointerEvent.Action.Move, 605f, 205f)
        pointer(PointerEvent.Action.Up, 605f, 205f)

        assertEquals(1, list.onItemTap)
    }

    @Test fun cancelReleasesTheBinding() {
        pointer(PointerEvent.Action.Down, 600f, 200f)
        pointer(PointerEvent.Action.Unknown, 600f, 200f)

        // Above every item: a stale binding would still hand this tap to the first item.
        pointer(PointerEvent.Action.Down, 600f, 100f)
        pointer(PointerEvent.Action.Up, 600f, 100f)

        assertEquals(0, list.onItemTap)
    }
}
