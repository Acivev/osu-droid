package com.osudroid.ui.verktex

import android.graphics.Paint
import android.graphics.Typeface
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
     * gets the default size the old font store used (35), like any unknown name.
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

    /**
     * Line height of the legacy font [name], measured the way the old AndEngine fonts did: ascent and
     * descent of a Paint with the default typeface and the size divided by the texture quality as an
     * integer division.
     */
    @JvmStatic
    fun lineHeight(name: String): Float {
        val paint = Paint()
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
        paint.isAntiAlias = true
        paint.textSize = (fontSize(name).toInt() / Config.getTextureQuality()).toFloat()
        val fm = paint.fontMetrics
        return -fm.ascent + fm.descent
    }
}
