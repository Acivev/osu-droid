package com.osudroid.ui.verktex

import android.graphics.Paint
import android.graphics.Typeface
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.GraphicsMode
import org.robolectric.annotation.Implementation
import org.robolectric.annotation.Implements
import ru.nsu.ccfit.zuev.osu.Config
import org.robolectric.annotation.Config as RobolectricConfig

/**
 * The song menu stacks its info texts by the line height the old AndEngine fonts reported, which is
 * the ascent and descent of a Paint with the default typeface and the integer-divided text size.
 * Runs at texture quality 2, where the integer division differs from a float one.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@RobolectricConfig(shadows = [LegacyUnitsLineHeightTest.TextureQualityTwo::class], instrumentedPackages = ["ru.nsu.ccfit.zuev.osu.Config"])
class LegacyUnitsLineHeightTest {

    @Implements(Config::class)
    class TextureQualityTwo {
        companion object {
            @JvmStatic
            @Implementation
            fun getTextureQuality(): Int = 2
        }
    }

    private fun reference(textSize: Float): Float {
        val paint = Paint()
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
        paint.isAntiAlias = true
        paint.textSize = textSize
        val fm = paint.fontMetrics
        return -fm.ascent + fm.descent
    }

    @Test
    fun lineHeightMatchesPaintMetrics() {
        assertEquals(2, Config.getTextureQuality())

        val font = LegacyUnits.lineHeight("font")
        assertTrue(font > 0f)
        assertEquals(reference(14f), font, 0f)

        // 21 / textureQuality is an integer division, as the old font store did it: 10, not 10.5.
        val small = LegacyUnits.lineHeight("smallFont")
        assertTrue(small > 0f)
        assertEquals(reference(10f), small, 0f)
        assertTrue(reference(10f) != reference(10.5f))
    }
}
