package com.osudroid.verktex.songmenu

import com.osudroid.ui.verktex.songmenu.BoardScroller
import org.junit.Assert.assertEquals
import org.junit.Test

class BoardScrollerTest {
    private val s = BoardScroller(720f)

    @Test fun scrollClampsAtTopAndMaxY() {
        s.maxY = 500f
        s.onScrollStarted(0, 0f)
        s.onScroll(0, 1000f); assertEquals(-146f, s.camY, 1e-4f)
        s.onScroll(0, -5000f); assertEquals(500f, s.camY, 1e-4f); assertEquals(0f, s.velocityY, 0f)
    }

    @Test fun otherPointerIsIgnored() {
        s.onScrollStarted(0, 0f); s.onScroll(1, 50f)
        assertEquals(0f, s.camY, 0f)
    }

    @Test fun finishGivesNegatedAverageVelocity() {
        s.maxY = 10000f
        s.onScrollStarted(0, 1f); s.onScroll(0, -50f); s.onScrollFinished(0, -50f, 1.5f)
        assertEquals(200f, s.velocityY, 1e-3f)
    }

    @Test fun finishWithinOneMillisecondGivesZero() {
        s.onScrollStarted(0, 1f); s.onScrollFinished(0, -50f, 1.0005f)
        assertEquals(0f, s.velocityY, 0f)
    }

    @Test fun updateDecaysAndStopsBelowThirty() {
        s.velocityY = 100f; s.update(0.05f, 5000f, 132f)
        assertEquals(60f, s.velocityY, 1e-3f)
        s.velocityY = 30f; s.update(0.05f, 5000f, 132f)
        assertEquals(0f, s.velocityY, 0f)
    }

    @Test fun updateBouncesAtTop() {
        s.camY = -146f; s.velocityY = -200f; s.update(0.05f, 5000f, 132f)
        assertEquals(-146f, s.camY, 1e-3f)
        assertEquals(0f, s.velocityY, 0f)
    }

    @Test fun maxYFormula() {
        s.update(0.016f, 2000f, 132f)
        assertEquals(2000f - 0.8f * (720f - 110f - 100f), s.maxY, 1e-3f)
    }
}
