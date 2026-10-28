package com.osudroid.game

import com.reco1l.verktex.time.TickingClock

/**
 * The gameplay clock as a Verktex [TickingClock]: it reads [clock]'s frame delta and time and never
 * processes a frame itself, the gameplay loop does that.
 */
class GameplayTickingClock(private val clock: GameplayFrameStabilityClock) : TickingClock() {

    override val elapsedTickTime get() = clock.elapsedFrameTime

    override val lastTickTime get() = clock.currentTime

    override val time get() = clock.currentTime

    override val isRunning get() = clock.isRunning
}
