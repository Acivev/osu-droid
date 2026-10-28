package com.osudroid.verktex.hud

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import ru.nsu.ccfit.zuev.osu.menu.PauseMenu

class PauseMenuTest {

    @Test
    fun buttonsScaleToEightyFivePercent() {
        assertEquals(612f / 1000f, PauseMenu.layoutScale(1000f, 720f), 1e-6f)
        assertEquals(1f, PauseMenu.layoutScale(500f, 720f), 0f)
    }

    @Test
    fun failMenuShowsSaveReplayUnlessReplaying() {
        assertEquals(
            listOf("pause-save-replay", "pause-retry", "pause-back"),
            PauseMenu.visibleButtons(true, false)
        )
        assertEquals(listOf("pause-retry", "pause-back"), PauseMenu.visibleButtons(true, true))
        assertEquals(listOf("pause-continue", "pause-retry", "pause-back"), PauseMenu.visibleButtons(false, false))
    }

    @Test
    fun dragFartherThanFiftyPxCancelsTheClick() {
        val tracker = PauseMenu.SelectionTracker()

        tracker.down(0f, 0f)
        tracker.move(60f, 0f)
        assertFalse(tracker.up())

        tracker.down(0f, 0f)
        tracker.move(30f, 0f)
        assertTrue(tracker.up())
    }

    @Test
    fun upWithoutPressDoesNotClick() {
        val tracker = PauseMenu.SelectionTracker()

        assertFalse(tracker.up())

        tracker.down(0f, 0f)
        assertTrue(tracker.up())
        assertFalse(tracker.up())
    }

    @Test
    fun cancelDropsTheSelectionWithoutClicking() {
        val tracker = PauseMenu.SelectionTracker()

        tracker.down(0f, 0f)
        tracker.cancel()
        assertFalse(tracker.up())
    }
}
