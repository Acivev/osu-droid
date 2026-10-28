package com.osudroid.verktex.game

import com.osudroid.game.CursorEvent
import com.osudroid.mods.ModHardRock
import com.reco1l.verktex.input.PointerEvent
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import ru.nsu.ccfit.zuev.osu.Config
import ru.nsu.ccfit.zuev.osu.Constants
import ru.nsu.ccfit.zuev.osu.game.GameHelper

/**
 * A [CursorEvent] built from a Verktex [PointerEvent] and the playfield coordinates of its point.
 */
@RunWith(RobolectricTestRunner::class)
class CursorEventTest {

    private var previousWidth = 0
    private var previousHeight = 0

    @Before fun setUp() {
        previousWidth = Config.getRES_WIDTH()
        previousHeight = Config.getRES_HEIGHT()
        Config.setRES_WIDTH(1280)
        Config.setRES_HEIGHT(720)
        GameHelper.setHardRock(null)
    }

    @After fun tearDown() {
        GameHelper.setHardRock(null)
        Config.setRES_WIDTH(previousWidth)
        Config.setRES_HEIGHT(previousHeight)
    }

    private fun pointer(action: PointerEvent.Action, x: Float, y: Float, time: Long) =
        PointerEvent(action = action, pointerId = 0, x = x, y = y, time = time)

    /** The MAP_ACTUAL to MAP mapping of the track coordinates. */
    private fun trackX(x: Float) = (x - (1280f - Constants.MAP_ACTUAL_WIDTH) / 2f) * (Constants.MAP_WIDTH / Constants.MAP_ACTUAL_WIDTH.toFloat())

    private fun trackY(y: Float) = (y - (720f - Constants.MAP_ACTUAL_HEIGHT) / 2f) * (Constants.MAP_HEIGHT / Constants.MAP_ACTUAL_HEIGHT.toFloat())

    @Test fun mapsWindowPointToTrackCoordinates() {
        val event = CursorEvent.obtain(pointer(PointerEvent.Action.Down, 640f, 360f, 123), 640f, 360f)

        assertEquals(640f, event.position.x, 0f)
        assertEquals(360f, event.position.y, 0f)
        assertEquals(trackX(640f), event.trackPosition.x, 1e-4f)
        assertEquals(trackY(360f), event.trackPosition.y, 1e-4f)
        assertEquals(123L, event.systemTime)
        assertTrue(event.isActionDown)
    }

    @Test fun clampsPositionToTheResolution() {
        val event = CursorEvent.obtain(pointer(PointerEvent.Action.Move, -10f, 360f, 0), -10f, 360f)

        assertEquals(0f, event.position.x, 0f)
        assertEquals(trackX(-10f), event.trackPosition.x, 1e-4f)
        assertTrue(event.isActionMove)
    }

    @Test fun hardRockFlipsTheTrackY() {
        GameHelper.setHardRock(ModHardRock())

        val event = CursorEvent.obtain(pointer(PointerEvent.Action.Up, 640f, 100f, 0), 640f, 100f)

        assertEquals(100f, event.position.y, 0f)
        assertEquals(trackY(720f - 100f), event.trackPosition.y, 1e-4f)
        assertTrue(event.isActionUp)
    }
}
