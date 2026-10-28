package com.osudroid.ui.v2.hud.elements

import com.osudroid.ui.verktex.LegacyUnits.vu
import com.reco1l.verktex.data.Color4
import com.reco1l.verktex.data.px
import com.reco1l.verktex.graphics.GraphicsRenderer
import com.reco1l.verktex.ui.shape.UIBox
import com.osudroid.beatmaps.constants.HitObjectType

class HUDColorHitErrorMeter : HUDHitErrorMeter() {
    private val indicators = MutableList(20) { Indicator(it, 0f, Color4.Black) }
    private var currentIndicatorIndex = 0

    // Indicators are drawn as squares of this size, vertically centered like the shared AndEngine box was.
    private val indicatorSize = INDICATOR_SIZE.vu

    init {
        // Extra 2 to indicator spacing for left and right padding.
        val width = INDICATOR_SIZE * indicators.size + INDICATOR_SPACING * (indicators.size + 2)

        this.width = width.vu.px
        this.height = INDICATOR_SIZE.vu.px

        background = UIBox().apply {
            color = Color4.Black
            alpha = 0.6f
        }
    }

    override fun addResult(type: HitObjectType, accuracy: Float, color: Color4) {
        indicators.forEach { it.alpha = (it.alpha - ALPHA_RAMP).coerceAtLeast(0f) }

        val indicator = indicators[currentIndicatorIndex]

        indicator.alpha = 1f
        indicator.color = color

        currentIndicatorIndex = (currentIndicatorIndex + 1) % indicators.size
    }

    override fun onSeek() {
        // Note that this only clears current active indicators. While reconstructing indicators that should still be
        // active is possible, it is not worth the complexity.
        indicators.forEach { it.alpha = 0f }
    }

    override fun onDraw(renderer: GraphicsRenderer) {
        super.onDraw(renderer)

        // Drawn after the children in this element's local space.
        indicators.forEach {
            renderer.drawRect(
                (INDICATOR_SPACING + it.index * (INDICATOR_SIZE + INDICATOR_SPACING)).vu,
                (bounds.height - indicatorSize) / 2f,
                indicatorSize,
                indicatorSize,
                color = it.color.copy(alpha = it.alpha)
            )
        }
    }

    private data class Indicator(val index: Int, var alpha: Float, var color: Color4)

    companion object {
        private const val INDICATOR_SIZE = 30f
        private const val INDICATOR_SPACING = 3f
        private const val ALPHA_RAMP = 0.08f
    }
}