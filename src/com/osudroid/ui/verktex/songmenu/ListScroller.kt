package com.osudroid.ui.verktex.songmenu

import kotlin.math.abs
import kotlin.math.sign

/**
 * Scroll physics of the song list, ported from the legacy `SongMenu`. All values are in virtual units.
 *
 * @param toRes Converts a value to the current resolution (legacy `Utils.toRes`).
 */
class ListScroller(
    private val resWidth: Float,
    private val resHeight: Float,
    private val toRes: (Float) -> Float
) {
    var camY = 0f
    var velocityY = 0f
    var maxY = 0f

    private var pointerId = -1
    private var touchY = 0f
    private var initialY = -1f
    private var tapTime = 0f

    val isDragging: Boolean
        get() = pointerId != -1

    /** Legacy: `if (evt.getX() < RES_WIDTH / 5f * 2) return false`. */
    fun acceptsX(x: Float) = x >= resWidth / 5f * 2

    /** Legacy ACTION_DOWN: `velocityY = 0; touchY = initalY = y; pointerId = id; tapTime = secPassed`. */
    fun onDown(pointerId: Int, y: Float, time: Float) {
        velocityY = 0f
        touchY = y
        this.pointerId = pointerId
        tapTime = time
        initialY = y
    }

    /**
     * Legacy ACTION_MOVE. A pointer other than the tracked one is ignored; with `initalY == -1` the move re-arms
     * like a down. Then `camY -= y - touchY`, clamped to `[-RES_HEIGHT / 2, maxY]` with `velocityY = 0` at a clamp.
     */
    fun onMove(pointerId: Int, y: Float, time: Float) {
        if (this.pointerId != -1 && this.pointerId != pointerId) {
            return
        }

        if (initialY == -1f) {
            velocityY = 0f
            touchY = y
            initialY = y
            tapTime = time
            this.pointerId = pointerId
        }

        camY -= y - touchY
        touchY = y

        if (camY <= -resHeight / 2f) {
            camY = -resHeight / 2f
            velocityY = 0f
        } else if (camY >= maxY) {
            camY = maxY
            velocityY = 0f
        }
    }

    /**
     * Legacy default branch: `velocityY = (initalY - y) / (secPassed - tapTime)`, or 0 when
     * `secPassed - tapTime < 0.001f || initalY == -1`. Releases the tracked pointer.
     */
    fun onUp(pointerId: Int, y: Float, time: Float) {
        if (this.pointerId != -1 && this.pointerId != pointerId) {
            return
        }

        velocityY = if (time - tapTime < 0.001f || initialY == -1f) 0f else (initialY - y) / (time - tapTime)
        this.pointerId = -1
        initialY = -1f
    }

    /** Legacy `stopScroll`: `velocityY = 0; touchY = y; initalY = -1`, the next move re-arms like a down. */
    fun stopScroll(y: Float) {
        velocityY = 0f
        touchY = y
        initialY = -1f
    }

    /**
     * Legacy `onUpdate`: `camY += v * dt; maxY = contentHeight - RES_HEIGHT / 2;` when
     * `camY <= -RES_HEIGHT / 2 && v < 0 || camY >= maxY && v > 0` undo the step and `v = 0`; then
     * `if (|v| > toRes(1000) * dt) v -= toRes(1000) * dt * signum(v) else v = 0`.
     */
    fun update(dt: Float, contentHeight: Float) {
        camY += velocityY * dt
        maxY = contentHeight - resHeight / 2f

        if (camY <= -resHeight / 2f && velocityY < 0 || camY >= maxY && velocityY > 0) {
            camY -= velocityY * dt
            velocityY = 0f
        }

        if (abs(velocityY) > toRes(1000f) * dt) {
            velocityY -= toRes(1000f) * dt * velocityY.sign
        } else {
            velocityY = 0f
        }
    }

    /**
     * Legacy `select`: `velocityY = 0; camY = heightBefore - RES_HEIGHT / 2; camY += item.getTotalHeight() / 2`.
     *
     * @param heightBefore Summed initial heights of the items above the selected one.
     */
    fun centerOn(heightBefore: Float, totalHeight: Float) {
        velocityY = 0f
        camY = heightBefore - resHeight / 2f
        camY += totalHeight / 2f
    }

    /** Legacy `updateScrollbar`: `setVisible(|velocityY| > toRes(500))`. */
    val isScrollbarVisible: Boolean
        get() = abs(velocityY) > toRes(500f)
}
