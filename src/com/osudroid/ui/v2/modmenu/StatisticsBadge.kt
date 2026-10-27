package com.osudroid.ui.v2.modmenu

import com.edlplan.framework.easing.Easing
import com.osudroid.ui.verktex.LegacyUnits
import com.osudroid.ui.verktex.LegacyUnits.vu
import com.reco1l.framework.interpolate
import com.reco1l.verktex.data.Anchor
import com.reco1l.verktex.data.Color4
import com.reco1l.verktex.data.Padding
import com.reco1l.verktex.data.px
import com.reco1l.verktex.time.TickingClock
import com.reco1l.verktex.ui.container.UILinearContainer
import com.reco1l.verktex.ui.text.UIText
import com.rian.framework.RollingFloatCounter

/**
 * A statistic badge is a badge that displays a value next to a label.
 */
class StatisticsBadge(
    private val formatter: (Float) -> String = { "%.2f".format(it) }
) : UILinearContainer() {

    /**
     * The entity of the badge's label.
     */
    val labelEntity = UIText().apply {
        alignment = Anchor.Center
        wrapText = false
        backgroundColor = Color4(0f, 0f, 0f, 0.1f)
    }

    /**
     * The value of the badge.
     */
    val valueEntity = UIText().apply {
        alignment = Anchor.Center
        wrapText = false
    }

    //region Shortcuts

    /**
     * The label of the badge.
     */
    var label by labelEntity::text

    /**
     * The value of the badge.
     */
    var value by valueEntity::text

    //endregion

    private val counter = RollingFloatCounter(0f).apply {
        rollingEasing = Easing.OutQuint
        rollingDuration = 0.3f
    }

    // AndEngine multiplied the children's colour by the badge's accent colour, Verktex only
    // passes the alpha down, so the tint is applied to the texts here.
    private var accent = Color4.White

    // The value colour transition of the AndEngine colorTo(), which only touches RGB.
    private var valueColor = Color4.White
    private var valueColorStart = Color4.White
    private var valueColorTarget = Color4.White
    private var valueColorElapsed = 0f

    // LegacyUnits.scale the sizes were last applied for.
    private var appliedScale = 0f

    init {
        orientation = Orientation.Horizontal

        style = Style { theme ->
            accent = theme.palette.accent
            backgroundColor = accent.copy(
                red = accent.red * 0.15f,
                green = accent.green * 0.15f,
                blue = accent.blue * 0.15f
            )
            labelEntity.color = accent
            valueEntity.color = valueColor.tinted()
        }

        this += labelEntity
        this += valueEntity
    }

    fun updateValue(initialValue: Float, finalValue: Float) {
        if (counter.targetValue == finalValue) {
            return
        }

        counter.targetValue = finalValue

        valueColorStart = valueColor
        valueColorTarget = Color4(when {
            initialValue < finalValue -> 0xFFF78383
            initialValue > finalValue -> 0xFF40CF5D
            else -> 0xFFFFFFFF
        })
        valueColorElapsed = 0f
    }

    override fun onUpdate(clock: TickingClock) {
        // Medium size variant of the AndEngine badge, in virtual units.
        if (appliedScale != LegacyUnits.scale) {
            appliedScale = LegacyUnits.scale

            val fontSize = LegacyUnits.fontSize("smallFont").vu.px
            val padding = Padding(12f.vu.px, 8f.vu.px)
            val radius = 12f.vu.px

            labelEntity.fontSize = fontSize
            valueEntity.fontSize = fontSize
            labelEntity.padding = padding
            valueEntity.padding = padding
            labelEntity.radius = radius
            this.radius = radius
        }

        if (counter.isRolling) {
            valueEntity.text = formatter(counter.currentValue)
        }

        counter.onUpdate(clock.elapsedTickTime)

        if (valueColor != valueColorTarget) {
            valueColorElapsed = (valueColorElapsed + clock.elapsedTickTime).coerceAtMost(counter.rollingDuration)

            val progress = counter.rollingEasing.interpolate(valueColorElapsed / counter.rollingDuration)

            valueColor = Color4(
                red = valueColorStart.red + (valueColorTarget.red - valueColorStart.red) * progress,
                green = valueColorStart.green + (valueColorTarget.green - valueColorStart.green) * progress,
                blue = valueColorStart.blue + (valueColorTarget.blue - valueColorStart.blue) * progress
            )

            if (valueColorElapsed == counter.rollingDuration) {
                valueColor = valueColorTarget
            }
        }

        valueEntity.color = valueColor.tinted()

        super.onUpdate(clock)
    }

    private fun Color4.tinted() = Color4(red * accent.red, green * accent.green, blue * accent.blue, alpha)
}
