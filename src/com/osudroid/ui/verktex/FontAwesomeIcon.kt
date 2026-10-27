package com.osudroid.ui.verktex

import com.osudroid.ui.verktex.LegacyUnits.vu
import com.reco1l.verktex.data.px
import com.reco1l.verktex.time.TickingClock
import com.reco1l.verktex.ui.UIIcon
import kotlin.math.min

/**
 * The AndEngine FontAwesomeIcon: a solid glyph of 24 virtual units scaled down to fit a square of
 * [boxSize] virtual units, centred in it.
 */
open class FontAwesomeIcon(icon: Int) : UIIcon(icon) {

    /**
     * The size of the square the glyph fits in, in virtual units.
     */
    var boxSize = 24f

    // LegacyUnits.scale and boxSize the sizes were last applied for.
    private var appliedScale = 0f
    private var appliedBoxSize = 0f

    override fun onUpdate(clock: TickingClock) {
        if (appliedScale != LegacyUnits.scale || appliedBoxSize != boxSize) {
            appliedScale = LegacyUnits.scale
            appliedBoxSize = boxSize

            val font = iconFamily[ICON_SIZE.vu.px]
            val fit = min(boxSize.vu / font[iconCode].advance, boxSize.vu / font.metrics.lineHeight)

            iconSize = (ICON_SIZE.vu * fit).px
            width = boxSize.vu.px
            height = boxSize.vu.px
        }

        super.onUpdate(clock)
    }

    companion object {
        // The AndEngine icon font size.
        private const val ICON_SIZE = 24f
    }
}
