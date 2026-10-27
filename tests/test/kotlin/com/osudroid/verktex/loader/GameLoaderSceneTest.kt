package com.osudroid.verktex.loader

import com.osudroid.ui.v2.GameLoaderScene
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GameLoaderSceneTest {

    private val t0 = 1_000_000L

    private val posted = mutableListOf<Runnable>()

    private var starts = 0

    private fun clock(isRestart: Boolean = false) = GameLoaderScene.LoaderClock(isRestart).also {
        it.post = { runnable -> posted += runnable }
        it.lastTimeTouched = t0
    }

    private fun GameLoaderScene.LoaderClock.tick(
        now: Long,
        isReadyToStart: Boolean,
        isMultiplayer: Boolean = false,
        isStoryboardLoading: Boolean = false,
        deltaTimeSec: Float = 0.016f,
        onTimeout: Runnable = Runnable {}
    ) = update(deltaTimeSec, isMultiplayer, isReadyToStart, isStoryboardLoading, now, { starts++ }, onTimeout)


    @Test fun soloStartsAfterTheMinimumTimeoutOnceReadyAndNoStoryboardLoading() {
        val clock = clock()

        // Loading finishes at t0, the minimum timeout counts from there.
        clock.tick(t0, isReadyToStart = false)

        clock.tick(t0 + 1999, isReadyToStart = true)
        assertEquals(0, starts)
        assertFalse(clock.isStarting)

        clock.tick(t0 + 2001, isReadyToStart = true, isStoryboardLoading = true)
        assertEquals(0, starts)

        clock.tick(t0 + 2001, isReadyToStart = true)
        assertEquals(1, starts)
        assertTrue(clock.isStarting)

        // Started once only.
        clock.tick(t0 + 3000, isReadyToStart = true)
        assertEquals(1, starts)
        assertTrue(posted.isEmpty())
    }

    @Test fun restartUsesHalfASecondUntilThePanelIsTouched() {
        val clock = clock(isRestart = true)
        assertEquals(500L, clock.minimumTimeout)
        assertEquals(500L, clock.fadeTimeout)

        clock.tick(t0, isReadyToStart = false)
        clock.tick(t0 + 501, isReadyToStart = true)
        assertEquals(1, starts)

        val touched = clock(isRestart = true)
        touched.onPanelTouched(t0)
        assertEquals(1500L, touched.minimumTimeout)
        assertEquals(1500L, touched.fadeTimeout)
        assertEquals(t0, touched.lastTimeTouched)

        touched.tick(t0 + 1499, isReadyToStart = true)
        assertEquals(1, starts)
        touched.tick(t0 + 1501, isReadyToStart = true)
        assertEquals(2, starts)

        // A first play keeps its two seconds when touched.
        val solo = clock()
        solo.onPanelTouched(t0)
        assertEquals(2000L, solo.minimumTimeout)
        assertEquals(2000L, solo.fadeTimeout)
    }

    @Test fun multiplayerStartsAtOnceWhenReady() {
        val clock = clock()

        clock.tick(t0, isReadyToStart = false, isMultiplayer = true)
        clock.tick(t0 + 1, isReadyToStart = true, isMultiplayer = true, isStoryboardLoading = true)

        assertEquals(1, starts)
        assertTrue(clock.isStarting)
    }

    @Test fun multiplayerTimeoutCancelsAfterSixtySeconds() {
        val clock = clock()
        var cancels = 0
        val onTimeout = Runnable { cancels++ }

        clock.tick(t0, isReadyToStart = false, isMultiplayer = true, deltaTimeSec = 59.9f, onTimeout = onTimeout)
        assertTrue(posted.isEmpty())

        clock.tick(t0, isReadyToStart = false, isMultiplayer = true, deltaTimeSec = 0.1f, onTimeout = onTimeout)
        assertEquals(1, posted.size)

        // Posted, not run inside the update.
        assertEquals(0, cancels)
        posted.single().run()
        assertEquals(1, cancels)

        clock.tick(t0, isReadyToStart = false, isMultiplayer = true, deltaTimeSec = 1f, onTimeout = onTimeout)
        assertEquals(1, posted.size)
        assertEquals(0, starts)

        // Solo never times out.
        val solo = clock()
        solo.tick(t0, isReadyToStart = false, deltaTimeSec = 120f, onTimeout = onTimeout)
        assertEquals(1, posted.size)
    }

    @Test fun startIsDeferredOutOfTheVerktexUpdate() {
        val clock = clock()
        var handedOver = 0
        val handOver = Runnable { handedOver++ }

        // Nothing happens before the start.
        clock.updateDimFade(1f, handOver)
        assertTrue(posted.isEmpty())

        clock.tick(t0, isReadyToStart = true, isMultiplayer = true)
        assertEquals(1, starts)

        // The dim fades for 0.2 s, then the hand-over is posted to the update thread.
        clock.updateDimFade(0.1f, handOver)
        assertTrue(posted.isEmpty())

        clock.updateDimFade(0.1f, handOver)
        assertEquals(1, posted.size)
        assertEquals(0, handedOver)

        posted.single().run()
        assertEquals(1, handedOver)

        clock.updateDimFade(0.1f, handOver)
        assertEquals(1, posted.size)
    }

    @Test fun handOverIsSkippedWhenTheLoaderWasCancelledFirst() {
        val clock = clock()
        var isShown = true
        clock.runIfShown = { body -> if (isShown) body.run() }

        var handedOver = 0
        clock.tick(t0, isReadyToStart = true, isMultiplayer = true)
        clock.updateDimFade(0.2f) { handedOver++ }
        assertEquals(1, posted.size)

        // A Back hop queued ahead of the hand-over cancels the loader first.
        isShown = false
        posted.single().run()
        assertEquals(0, handedOver)
    }

    @Test fun timeoutCancelRunsOnce() {
        val clock = clock()
        var isShown = true
        clock.runIfShown = { body -> if (isShown) body.run() }

        var cancels = 0
        val onTimeout = Runnable {
            cancels++
            isShown = false
        }

        clock.tick(t0, isReadyToStart = false, isMultiplayer = true, deltaTimeSec = 60f, onTimeout = onTimeout)
        clock.tick(t0, isReadyToStart = false, isMultiplayer = true, deltaTimeSec = 1f, onTimeout = onTimeout)
        assertEquals(1, posted.size)

        // Even if the body runs again, the loader it cancelled is no longer shown.
        posted.single().run()
        posted.single().run()
        assertEquals(1, cancels)
    }

    @Test fun panelFadesToHalfAfterTheTimeout() {
        val clock = clock()
        clock.onPanelTouched(t0)

        assertEquals(1f, clock.fadePanel(1f, 0.2f, t0 + 2000), 0f)

        val alpha = clock.fadePanel(1f, 0.2f, t0 + 2001)
        assertEquals(0.7f, alpha, 1e-6f)

        // The fade stops only once below 0.5, it undershoots.
        val undershot = clock.fadePanel(alpha, 0.2f, t0 + 2001)
        assertEquals(0.4f, undershot, 1e-6f)
        assertEquals(undershot, clock.fadePanel(undershot, 0.2f, t0 + 2001), 0f)
    }
}
