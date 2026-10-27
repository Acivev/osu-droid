package com.osudroid.ui.v2.modmenu

import com.edlplan.framework.easing.Easing as LegacyEasing
import com.osudroid.ui.verktex.LegacyUnits
import com.osudroid.ui.verktex.LegacyUnits.vu
import com.osudroid.ui.verktex.times
import com.reco1l.framework.interpolate
import com.reco1l.verktex.data.Color4
import com.reco1l.verktex.data.Padding
import com.reco1l.verktex.data.px
import com.reco1l.verktex.data.wrap
import com.reco1l.verktex.math.Easing
import com.reco1l.verktex.math.FloatAnimation
import com.reco1l.verktex.time.TickingClock
import com.reco1l.verktex.ui.UIButton

/**
 * The look of the AndEngine UIButton: background and content colours from the selection, the
 * linear content colour and enabled fades, and the press scale. Verktex only passes the alpha
 * down, so subclasses tint their content in [applyTint].
 */
abstract class LegacyButton : UIButton() {

    private var tint = Color4.White
    private var tintStart = Color4.White
    private var tintTarget = Color4.White
    private var tintElapsed = TINT_DURATION
    private var styledSelected: Boolean? = null

    // The style first runs from this constructor, before the subclass content exists, so the
    // tint is applied in onUpdate.
    private var isTintPending = true

    private val scaleAnimation = FloatAnimation(1f, 0.3f)
    private val enabledAlpha = FloatAnimation(1f, 0.2f, Easing.None)
    private var wasPressed = false

    // LegacyUnits.scale the sizes were last applied for.
    private var appliedScale = 0f

    init {
        // The UIButton style set the 40 dip control height, the AndEngine button wrapped its content.
        height = wrap()

        style = Style { theme ->
            val accent = theme.palette.accent

            backgroundColor = if (isSelected) accent else accent * 0.175f

            val target = if (isSelected) accent * 0.1f else accent

            // A selection change fades the colour like onSelectionChange(), a theme change applies it.
            if (styledSelected != null && styledSelected != isSelected) {
                tintStart = tint
                tintTarget = target
                tintElapsed = 0f
            } else {
                tint = target
                tintTarget = target
                tintElapsed = TINT_DURATION
                isTintPending = true
            }
            styledSelected = isSelected
        }
    }

    /**
     * Applies the sizes given in virtual units, called whenever [LegacyUnits.scale] changes.
     */
    protected abstract fun onApplyLegacySizes()

    /**
     * Applies the button colour to the content.
     */
    protected abstract fun applyTint(tint: Color4)

    /**
     * Advances any extra alpha animation of the subclass and returns the factor the enabled alpha
     * is multiplied with.
     */
    protected open fun updateAlphaFactor(clock: TickingClock) = 1f

    override fun onUpdate(clock: TickingClock) {
        if (appliedScale != LegacyUnits.scale) {
            appliedScale = LegacyUnits.scale

            padding = Padding(12f.vu.px, 16f.vu.px)
            radius = 12f.vu.px
            onApplyLegacySizes()
        }

        // The touch feedback of the AndEngine UIButton.
        if (isPressed && !wasPressed) {
            scaleAnimation.animateTo(0.9f, 0.3f, PressEasing)
        } else if (!isPressed && wasPressed && scale != 1f) {
            scaleAnimation.animateTo(1f, 0.4f, ReleaseEasing)
        }
        wasPressed = isPressed

        if (tintElapsed < TINT_DURATION) {
            tintElapsed = (tintElapsed + clock.elapsedTickTime).coerceAtMost(TINT_DURATION)

            val progress = tintElapsed / TINT_DURATION

            tint = Color4(
                red = tintStart.red + (tintTarget.red - tintStart.red) * progress,
                green = tintStart.green + (tintTarget.green - tintStart.green) * progress,
                blue = tintStart.blue + (tintTarget.blue - tintStart.blue) * progress
            )
            isTintPending = true
        }

        if (isTintPending) {
            isTintPending = false
            applyTint(tint.copy(alpha = 1f))
        }

        // UIButton animates its own scale and alpha, the AndEngine ones replace them.
        super.onUpdate(clock)

        enabledAlpha.target = if (isEnabled) 1f else 0.5f

        scale = scaleAnimation.update(clock)
        alpha = enabledAlpha.update(clock) * updateAlphaFactor(clock)
    }

    companion object {
        private const val TINT_DURATION = 0.2f

        // UniversalModifier clamped the eased progress, so the elastic release never went past 1.
        private val PressEasing = Easing { LegacyEasing.Out.interpolate(it).coerceIn(0f, 1f) }
        private val ReleaseEasing = Easing { LegacyEasing.OutElastic.interpolate(it).coerceIn(0f, 1f) }
    }
}
