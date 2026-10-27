package com.osudroid.ui.verktex.songmenu

import kotlin.math.hypot

/**
 * The press bookkeeping the legacy song menu buttons and statistic texts shared: one press at a time
 * across all of them, cancelled once the pointer moves more than 50 virtual units. Coordinates are
 * local, in virtual units. Update thread only.
 */
internal class LegacyPressTracker(private val owner: Any) {

    /** Whether the last press was cancelled by moving away or by a cancel event. */
    var moved = false
        private set

    private var dx = 0f
    private var dy = 0f

    val isPressed: Boolean
        get() = currentPressedButton === owner

    /** Starts a press unless another one is held. Returns whether it started. */
    fun down(x: Float, y: Float): Boolean {
        if (currentPressedButton != null) {
            return false
        }

        currentPressedButton = owner
        moved = false
        dx = x
        dy = y
        return true
    }

    /** Ends the press. Returns whether it was the owner's. */
    fun up(): Boolean {
        if (!isPressed) {
            return false
        }

        currentPressedButton = null
        return true
    }

    /** Returns whether this move cancelled the owner's press. */
    fun move(x: Float, y: Float): Boolean {
        if (!isPressed || hypot(dx - x, dy - y) <= 50) {
            return false
        }

        cancel()
        return true
    }

    /** Drops the owner's press. Returns whether there was one. */
    fun cancel(): Boolean {
        if (!isPressed) {
            return false
        }

        currentPressedButton = null
        moved = true
        return true
    }

    companion object {

        /** The pressed button or statistic text, the legacy `currentPressedButton`. */
        var currentPressedButton: Any? = null
    }
}
