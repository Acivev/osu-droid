package com.osudroid.verktex.bridge

import android.os.PowerManager

/**
 * The screen wake lock the activity holds while the game runs.
 */
@Suppress("DEPRECATION")
enum class WakeLockMode(val flag: Int) {
    Dim(PowerManager.SCREEN_DIM_WAKE_LOCK),
    Bright(PowerManager.SCREEN_BRIGHT_WAKE_LOCK),
}
