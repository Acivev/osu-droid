package com.osudroid.ui.verktex.songmenu

import kotlin.math.abs
import kotlin.math.max

/**
 * Scroll physics of the leaderboard, ported from the legacy `ScoreBoard`. All values are in virtual units.
 */
class BoardScroller(private val resHeight: Float) {
    var camY = 0f
    var velocityY = 0f
    var maxY = 0f

    private var pointerId = -1
    private var tapTime = 0f
    private var cumulativeDistY = 0f

    /** Legacy `onScrollStarted`: `velocityY = 0; pointerId = id; tapTime = secPassed; cumulativeDistY = 0`. */
    fun onScrollStarted(pointerId: Int, time: Float) {
        velocityY = 0f
        this.pointerId = pointerId
        tapTime = time
        cumulativeDistY = 0f
    }

    /**
     * Legacy `onScroll`: other pointers are ignored; `camY -= distanceY`, clamped to `[-146, maxY]` with
     * `velocityY = 0` at a clamp; `cumulativeDistY += distanceY`.
     */
    fun onScroll(pointerId: Int, distanceY: Float) {
        if (this.pointerId != -1 && this.pointerId != pointerId) {
            return
        }

        camY -= distanceY

        if (camY <= -146f) {
            camY = -146f
            velocityY = 0f
        } else if (camY >= maxY) {
            camY = maxY
            velocityY = 0f
        }

        cumulativeDistY += distanceY
    }

    /**
     * Legacy `onScrollFinished`: `cumulativeDistY += distanceY`; `velocityY = -(cumulativeDistY / (secPassed - tapTime))`
     * when `secPassed - tapTime > 0.001f`, else 0. Releases the tracked pointer.
     */
    fun onScrollFinished(pointerId: Int, distanceY: Float, time: Float) {
        if (this.pointerId != -1 && this.pointerId != pointerId) {
            return
        }

        cumulativeDistY += distanceY
        velocityY = if (time - tapTime > 0.001f) -(cumulativeDistY / (time - tapTime)) else 0f
        this.pointerId = -1
    }

    /**
     * Legacy `onManagedUpdate`: `camY += v * dt; maxY = contentHeight - 0.8f * (RES_HEIGHT - 110 - (height - 32))`;
     * when `camY <= -146 && v < 0 || camY > maxY && v > 0` undo the step and `v = 0`; then
     * `if (|v| > 30) v *= max(0, 1 - 8 * dt) else v = 0`.
     *
     * @param itemHeight Height of a leaderboard item (legacy `height`).
     */
    fun update(dt: Float, contentHeight: Float, itemHeight: Float) {
        camY += velocityY * dt
        maxY = contentHeight - 0.8f * (resHeight - 110f - (itemHeight - 32f))

        if (camY <= -146f && velocityY < 0 || camY > maxY && velocityY > 0) {
            camY -= velocityY * dt
            velocityY = 0f
        }

        if (abs(velocityY) > 30f) {
            velocityY *= max(0f, 1f - 8f * dt)
        } else {
            velocityY = 0f
        }
    }
}
