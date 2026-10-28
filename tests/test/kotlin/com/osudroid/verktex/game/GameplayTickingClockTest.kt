package com.osudroid.verktex.game

import com.osudroid.game.FramedBeatmapClock
import com.osudroid.game.GameplayFrameStabilityClock
import com.osudroid.game.GameplayTickingClock
import com.rian.andengine.timing.TestClock
import org.junit.Assert.assertEquals
import org.junit.Test

class GameplayTickingClockTest {

    @Test
    fun exposesTheGameplayDeltaAndTime() {
        val source = TestClock().apply { start(); currentTime = 1f }
        val clock = GameplayFrameStabilityClock(FramedBeatmapClock(applyOffsets = false, source = source))
        val ticking = GameplayTickingClock(clock)

        // Without the per-frame clamp the gameplay delta is the source delta.
        clock.frameStablePlayback = false

        // The first frame only takes over the source time.
        clock.processFrame()

        // Past the interpolation's allowable error, so the reference clock takes the source time
        // instead of interpolating with the wall clock.
        source.currentTime += 0.05f
        clock.processFrame()

        assertEquals(clock.elapsedFrameTime, ticking.elapsedTickTime, 0f)
        assertEquals(0.05f, ticking.elapsedTickTime, 1e-4f)
        assertEquals(clock.currentTime, ticking.lastTickTime, 0f)
        assertEquals(clock.currentTime, ticking.time, 0f)
        assertEquals(clock.isRunning, ticking.isRunning)
    }
}
