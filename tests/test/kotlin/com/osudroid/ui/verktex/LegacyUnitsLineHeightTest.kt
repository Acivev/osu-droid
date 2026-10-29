package com.osudroid.ui.verktex

import android.graphics.Paint
import android.graphics.Typeface
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.GraphicsMode
import ru.nsu.ccfit.zuev.osu.Config

/**
 * The song menu stacks its info texts by the line height the old AndEngine fonts reported, which is
 * the ascent and descent of a Paint with the default typeface and the integer-divided text size.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class LegacyUnitsLineHeightTest {

    private fun reference(size: Int): Float {
        val paint = Paint()
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
        paint.isAntiAlias = true
        paint.textSize = (size / Config.getTextureQuality()).toFloat()
        val fm = paint.fontMetrics
        return -fm.ascent + fm.descent
    }

    @Test
    fun lineHeightMatchesPaintMetrics() {
        val font = LegacyUnits.lineHeight("font")
        assertTrue(font > 0f)
        assertEquals(reference(28), font, 0f)

        // 21 / textureQuality is an integer division, as the old font store did it.
        val small = LegacyUnits.lineHeight("smallFont")
        assertTrue(small > 0f)
        assertEquals(reference(21), small, 0f)
    }
}
