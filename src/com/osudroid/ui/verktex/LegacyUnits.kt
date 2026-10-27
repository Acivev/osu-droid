package com.osudroid.ui.verktex

import com.reco1l.verktex.Verktex
import ru.nsu.ccfit.zuev.osu.Config

/**
 * Converts the AndEngine virtual-resolution constants of the legacy scenes into window pixels.
 */
object LegacyUnits {

    /** windowHeight / Config.getRES_HEIGHT(); 1f while the window has no size yet. */
    @JvmStatic
    val scale: Float
        get() {
            val height = Verktex.graphics.window.windowHeight
            return if (height > 0) height.toFloat() / Config.getRES_HEIGHT() else 1f
        }

    /** Virtual-resolution value to window pixels. */
    val Float.vu: Float get() = this * scale

    val Int.vu: Float get() = this * scale

    /**
     * Font sizes of ResourceManager fonts, in virtual units. "logFont" is never registered, so it
     * gets the default size `FontStore.getOrLoadDefault` uses (35), like any unknown name.
     */
    @JvmStatic
    fun fontSize(name: String): Float = when (name) {
        "font" -> 28f
        "middleFont" -> 24f
        "smallFont" -> 21f
        "xs" -> 16f
        "strokeFont" -> 36f
        "bigFont" -> 36f
        "CaptionFont" -> 35f
        else -> 35f
    }
}
