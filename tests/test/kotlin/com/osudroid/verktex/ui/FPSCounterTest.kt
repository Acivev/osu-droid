package com.osudroid.verktex.ui

import com.osudroid.ui.FPSCounter
import com.osudroid.verktex.multi.LobbyTestSupport
import com.osudroid.verktex.multi.TestClock
import com.reco1l.verktex.Verktex
import com.reco1l.verktex.data.Color4
import com.rian.andengine.modifier.ModifierRegistry
import com.rian.andengine.timing.ManualClock
import com.rian.andengine.timing.ThrottledFrameClock
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import ru.nsu.ccfit.zuev.osu.Config

/**
 * FPSCounter.kt before the port: the text colour runs from the minimum colour through the middle one
 * (half the aimed frame rate) to the maximum one (90 % of it), and the counter fades to its idle alpha
 * once the frame rate has not changed significantly for 2 seconds.
 */
@RunWith(RobolectricTestRunner::class)
class FPSCounterTest {

    private val manualSource = ManualClock().apply { isRunning = true }
    private val frameClock = ThrottledFrameClock().apply {
        throttling = false
        changeSource(manualSource)
    }

    private var showFps = false

    private lateinit var counter: FPSCounter

    @Before fun setUp() {
        LobbyTestSupport.setUp()

        showFps = Config.isShowFPS()
        Config.setShowFPS(true)

        counter = Verktex.withTreeLock { FPSCounter() }.apply {
            clockProvider = { frameClock }
            refreshRateProvider = { 60f }
        }
    }

    @After fun tearDown() {
        Config.setShowFPS(showFps)
        LobbyTestSupport.tearDown()
    }

    /**
     * Runs [seconds] of frames at [fps] through the counter and the modifiers.
     */
    private fun run(fps: Float, seconds: Float) = Verktex.withTreeLock {
        val frameTime = 1f / fps
        val clock = TestClock(frameTime)

        repeat((seconds * fps).toInt()) {
            manualSource.currentTime += frameTime
            frameClock.processFrame()
            ModifierRegistry.tick(frameTime)
            counter.update(clock)
        }
    }

    private fun distance(a: Color4, b: com.reco1l.framework.Color4) =
        maxOf(kotlin.math.abs(a.red - b.red), kotlin.math.abs(a.green - b.green), kotlin.math.abs(a.blue - b.blue))


    @Test fun colourFollowsTheThresholds() {
        val minimum = com.reco1l.framework.Color4(0xed1121)
        val middle = com.reco1l.framework.Color4(0xebc247)
        val maximum = com.reco1l.framework.Color4(0xccff99)

        run(fps = 60f, seconds = 3f)
        assertTrue("${counter.color}", distance(counter.color, maximum) < 0.02f)

        run(fps = 30f, seconds = 3f)
        assertTrue("${counter.color}", distance(counter.color, middle) < 0.05f)

        run(fps = 2f, seconds = 5f)
        assertTrue("${counter.color}", distance(counter.color, minimum) < distance(counter.color, middle))
    }

    @Test fun hidesAfterTwoSecondsWithoutChange() {
        // Shown when it becomes visible, and kept shown while the frame rate settles.
        run(fps = 60f, seconds = 1.5f)
        assertEquals(1f, counter.alpha, 0.001f)

        // 2 seconds after the last significant change it fades to the idle alpha in 0.3 seconds.
        run(fps = 60f, seconds = 1.5f)
        assertEquals(0.7f, counter.alpha, 0.001f)

        // A spike shows it again.
        run(fps = 4f, seconds = 1f)
        assertEquals(1f, counter.alpha, 0.001f)
    }
}
