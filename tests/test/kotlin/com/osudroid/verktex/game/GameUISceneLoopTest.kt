package com.osudroid.verktex.game

import com.osudroid.game.FramedBeatmapClock
import com.osudroid.game.GameplayFrameStabilityClock
import com.rian.andengine.modifier.ModifierTicker
import com.rian.andengine.timing.FrameTimeInfo
import com.rian.andengine.timing.IFrameBasedClock
import com.rian.andengine.timing.Stopwatch
import com.rian.andengine.timing.TestClock
import org.junit.Assert.assertEquals
import org.junit.Test
import ru.nsu.ccfit.zuev.osu.game.GameScene

/**
 * The update loop of the gameplay scene, through its seam `GameScene.runGameplayLoop`: the clock is
 * processed every pass, the update, the playfield ticker and the children follow unless updates are
 * ignored, and passes repeat while the clock catches up for at most 10 ms.
 */
class GameUISceneLoopTest {

    private class FakeClock : IFrameBasedClock {
        var frames = 0
        var onFrame: () -> Unit = {}

        override var currentTime = 0f
        override val rate = 1f
        override val isRunning = true
        override val elapsedFrameTime = 0f
        override val framesPerSecond = 0f
        override val timeInfo get() = FrameTimeInfo(elapsedFrameTime, currentTime)

        override fun processFrame() {
            frames++
            onFrame()
        }
    }

    private val ticker = ModifierTicker()
    private val calls = mutableListOf<String>()

    private fun run(clock: IFrameBasedClock, catchUp: () -> Boolean, stopwatch: Stopwatch = Stopwatch(), ignore: Boolean = false) =
        GameScene.runGameplayLoop(clock, { catchUp() }, stopwatch, { ignore }, ticker, { calls += "update" }, { calls += "children:${ticker.time}" })

    @Test fun onePassWithoutCatchUp() {
        val clock = FakeClock().apply { currentTime = 2f }

        run(clock, { false })

        assertEquals(1, clock.frames)
        assertEquals(listOf("update", "children:2.0"), calls)
    }

    @Test fun boundedPassesWhenCatchingUp() {
        var nanos = 0L
        val clock = FakeClock().apply { onFrame = { nanos += 3_000_000L } }

        run(clock, { true }, Stopwatch { nanos })

        // 3, 6 and 9 ms are below the 10 ms allowance, the pass at 12 ms is the last one.
        assertEquals(4, clock.frames)
        assertEquals(8, calls.size)
    }

    @Test fun ignoredUpdateStillProcessesTheClock() {
        val clock = FakeClock().apply { currentTime = 5f }
        ticker.time = 1f

        run(clock, { false }, ignore = true)

        assertEquals(1, clock.frames)
        assertEquals(emptyList<String>(), calls)
        assertEquals(1f, ticker.time, 0f)
    }

    @Test fun playfieldTickerFollowsClockAcrossSeek() {
        val source = TestClock().apply { start(); currentTime = 10f }
        val clock = GameplayFrameStabilityClock(FramedBeatmapClock(applyOffsets = false, source = source))

        GameScene.runGameplayLoop(clock, { clock.requiresCatchUp }, Stopwatch(), { false }, ticker, {}, {})
        assertEquals(clock.currentTime, ticker.time, 0f)

        // A seek backwards to a negative lead-in time; the frame after it has no elapsed time.
        clock.seek(-1.5f)
        GameScene.runGameplayLoop(clock, { clock.requiresCatchUp }, Stopwatch(), { false }, ticker, {}, {})
        assertEquals(clock.currentTime, ticker.time, 0f)
        assertEquals(-1.5f, ticker.time, 1e-4f)
    }
}
