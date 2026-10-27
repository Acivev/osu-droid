package com.osudroid.verktex.songmenu

import com.osudroid.ui.verktex.songmenu.ListScroller
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ListScrollerTest {
    private val s = ListScroller(1280f, 720f) { it }

    @Test fun touchLeftOfTwoFifthsIsIgnored() { assertFalse(s.acceptsX(511f)); assertTrue(s.acceptsX(512f)) }

    @Test fun dragMovesCameraAgainstFinger() {
        s.onDown(0, 400f, 0f); s.onMove(0, 450f, 0.016f)
        assertEquals(-50f, s.camY, 1e-4f)
    }

    @Test fun dragClampsAtBothEnds() {
        s.maxY = 1000f; s.onDown(0, 0f, 0f); s.onMove(0, 2000f, 0.1f)
        assertEquals(-360f, s.camY, 1e-4f); assertEquals(0f, s.velocityY, 0f)
        s.onMove(0, -5000f, 0.2f); assertEquals(1000f, s.camY, 1e-4f)
    }

    @Test fun releaseGivesAverageVelocity() {
        s.onDown(0, 500f, 1f); s.onMove(0, 300f, 1.25f); s.onUp(0, 300f, 1.5f)
        assertEquals(400f, s.velocityY, 1e-3f)
        assertFalse(s.isDragging)
    }

    @Test fun releaseWithinOneMillisecondGivesZeroVelocity() {
        s.onDown(0, 500f, 1f); s.onUp(0, 300f, 1.0005f)
        assertEquals(0f, s.velocityY, 0f)
    }

    @Test fun secondPointerIsIgnoredWhileDragging() {
        s.onDown(0, 400f, 0f); s.onMove(1, 900f, 0.1f)
        assertEquals(0f, s.camY, 0f)
        assertTrue(s.isDragging)
    }

    @Test fun updateDeceleratesLinearlyAndStops() {
        s.velocityY = 1500f; s.update(0.5f, 10000f)
        assertEquals(1000f, s.velocityY, 1e-3f); assertEquals(750f, s.camY, 1e-3f)
        s.velocityY = 100f; s.update(0.5f, 10000f); assertEquals(0f, s.velocityY, 0f)
    }

    @Test fun updateBouncesBackAtTheEnds() {
        s.velocityY = -1000f; s.camY = -360f; s.update(0.1f, 10000f)
        assertEquals(-360f, s.camY, 1e-3f); assertEquals(0f, s.velocityY, 0f)
    }

    @Test fun centerOnPlacesSelectedItemInTheMiddle() { s.centerOn(1000f, 200f); assertEquals(740f, s.camY, 1e-4f) }

    @Test fun stopScrollHaltsAndRearmsOnTheNextMove() {
        s.maxY = 1000f; s.onDown(0, 500f, 0f); s.onMove(0, 400f, 0.1f); s.velocityY = 800f
        s.stopScroll(300f)
        assertEquals(0f, s.velocityY, 0f)
        s.onMove(0, 250f, 0.2f)
        assertEquals(100f, s.camY, 1e-4f)
        s.onUp(0, 200f, 0.3f)
        assertEquals(500f, s.velocityY, 1e-3f)
    }

    @Test fun scrollbarVisibleOnlyWhenFast() {
        s.velocityY = 501f; assertTrue(s.isScrollbarVisible)
        s.velocityY = 500f; assertFalse(s.isScrollbarVisible)
    }
}
