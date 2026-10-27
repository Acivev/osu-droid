package com.osudroid.ui.verktex

import com.edlplan.framework.easing.Easing
import com.osudroid.ui.OsuColors
import com.osudroid.ui.verktex.LegacyUnits.vu
import com.reco1l.verktex.data.Color4
import com.reco1l.verktex.data.Padding
import com.reco1l.verktex.data.px
import com.reco1l.verktex.graphics.GraphicsRenderer
import com.reco1l.verktex.graphics.GraphicsTexture
import com.reco1l.verktex.time.TickingClock
import com.reco1l.verktex.ui.UIBadge
import com.rian.framework.RollingDoubleCounter
import kotlin.math.abs
import kotlin.math.min

/**
 * A [UIBadge] for displaying star ratings. Automatically adjusts its styling according to the rating.
 */
class StarRatingBadge : UIBadge(), ISkinnable {

    private val counter = RollingDoubleCounter(0.0).apply {
        rollingEasing = Easing.OutQuint
    }

    /**
     * The star rating value displayed by this [StarRatingBadge].
     *
     * Visuals may not reflect this value due to rolling animation.
     */
    var rating
        get() = counter.targetValue
        set(value) {
            val prev = counter.targetValue

            if (prev != value) {
                counter.targetValue = value
                counter.rollingDuration = 0.1f + 0.08f * abs(value - prev).toFloat()
            }
        }

    // Verktex's leading icon is a font glyph, so the star texture is drawn in the left padding.
    private var star: GraphicsTexture.Region? = null
    private var starInvalid = true
    private var starSize = 0f

    // LegacyUnits.scale the sizes were last applied for.
    private var appliedScale = 0f

    init {
        // Badge color is determined by rating and should not be affected by themes.
        style = Style {}
        // The colours the rolling path produces for 0.0.
        backgroundColor = OsuColors.getStarRatingColor(0.0).toVerktex()
        color = Color4.Black
        alpha = 0.75f
        wrapText = false

        text = "0.00"
    }

    override fun onSkinChanged() {
        starInvalid = true
    }

    override fun onUpdate(clock: TickingClock) {
        if (starInvalid) {
            starInvalid = false
            star = SkinTextures.get("star-xs", "star.png")
        }

        // Medium size variant of the AndEngine badge, in virtual units.
        if (appliedScale != LegacyUnits.scale) {
            appliedScale = LegacyUnits.scale

            fontSize = LegacyUnits.fontSize("smallFont").vu.px
            starSize = fontFamily[fontSize, fontWeight, fontStyle].metrics.lineHeight
            padding = Padding(
                left = (12f.vu + starSize + 8f.vu).px,
                top = 8f.vu.px,
                right = 12f.vu.px,
                bottom = 8f.vu.px
            )
            radius = 12f.vu.px
        }

        if (counter.isRolling) {
            text = "%.2f".format(counter.currentValue)
            backgroundColor = OsuColors.getStarRatingColor(counter.currentValue).toVerktex()

            if (counter.currentValue >= 6.5) {
                color = OsuColors.getStarRatingTextColor(counter.currentValue).toVerktex()
                alpha = 1f
            } else {
                color = Color4.Black
                alpha = 0.75f
            }
        }

        counter.onUpdate(clock.elapsedTickTime)

        super.onUpdate(clock)
    }

    override fun onDraw(renderer: GraphicsRenderer) {
        super.onDraw(renderer)

        val star = star ?: return
        if (star.width <= 0f || star.height <= 0f) return

        // Fit inside a square of the text height, like the AndEngine leading icon sprite.
        val scale = min(starSize / star.width, starSize / star.height)
        val width = star.width * scale
        val height = star.height * scale

        val contentTop = paddingTop.toPixels()
        val contentHeight = bounds.height - contentTop - paddingBottom.toPixels()

        renderer.drawTexture(
            texture = star.texture,
            region = star,
            x = 12f.vu + (starSize - width) / 2f,
            y = contentTop + (contentHeight - height) / 2f,
            width = width,
            height = height,
            color = contentColor
        )
    }
}
