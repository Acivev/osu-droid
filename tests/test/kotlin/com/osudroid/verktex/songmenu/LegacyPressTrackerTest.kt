package com.osudroid.verktex.songmenu

import com.osudroid.ui.verktex.songmenu.LegacyPressTracker
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class LegacyPressTrackerTest {
    private val a = LegacyPressTracker("a")
    private val b = LegacyPressTracker("b")

    @Before
    @After
    fun resetGuard() {
        LegacyPressTracker.currentPressedButton = null
    }

    @Test
    fun onePressAtATime() {
        assertTrue(a.down(0f, 0f))
        assertFalse(b.down(0f, 0f))
        assertFalse(b.isPressed)
        assertTrue(a.up())
        assertTrue(b.down(0f, 0f))
    }

    @Test
    fun moveBeyondFiftyUnitsCancels() {
        a.down(0f, 0f)
        assertFalse(a.move(30f, 40f))
        assertFalse(a.moved)
        assertTrue(a.move(31f, 40f))
        assertTrue(a.moved)
        assertFalse(a.isPressed)
        assertNull(LegacyPressTracker.currentPressedButton)
    }

    @Test
    fun cancelSetsMovedAndReleasesTheGuard() {
        a.down(0f, 0f)
        assertTrue(a.cancel())
        assertTrue(a.moved)
        assertTrue(b.down(0f, 0f))
    }

    @Test
    fun upReleasesTheGuard() {
        a.down(0f, 0f)
        assertTrue(a.up())
        assertFalse(a.moved)
        assertNull(LegacyPressTracker.currentPressedButton)
        assertFalse(a.up())
    }
}
