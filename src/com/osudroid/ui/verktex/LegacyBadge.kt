package com.osudroid.ui.verktex

import androidx.annotation.StringRes
import com.osudroid.ui.verktex.LegacyUnits.vu
import com.reco1l.andengine.ui.SizeVariant
import com.reco1l.verktex.data.Anchor
import com.reco1l.verktex.data.Color4
import com.reco1l.verktex.data.Padding
import com.reco1l.verktex.data.px
import com.reco1l.verktex.ui.container.UIContainer
import com.reco1l.verktex.ui.container.UILinearContainer
import com.reco1l.verktex.ui.text.UIText
import ru.nsu.ccfit.zuev.osu.helper.StringTable

inline fun UIContainer.legacyBadge(builder: LegacyBadge.() -> Unit) = LegacyBadge().apply(builder).also { +it }

inline fun UIContainer.legacyLabeledBadge(builder: LegacyLabeledBadge.() -> Unit) = LegacyLabeledBadge().apply(builder).also { +it }

/**
 * The font, padding and corner radius of an AndEngine badge size variant, in virtual units.
 */
private class BadgeSizes(variant: SizeVariant) {
    val fontSize: Float
    val padding: Padding
    val radius: Float

    init {
        val font: String
        val horizontal: Float
        val vertical: Float

        when (variant) {
            SizeVariant.Small -> { font = "xs"; horizontal = 8f; vertical = 4f; radius = 6f }
            SizeVariant.Medium -> { font = "smallFont"; horizontal = 12f; vertical = 8f; radius = 12f }
            SizeVariant.Large -> { font = "font"; horizontal = 16f; vertical = 12f; radius = 16f }
        }

        fontSize = LegacyUnits.fontSize(font)
        padding = Padding(horizontal.vu.px, vertical.vu.px)
    }
}

/**
 * The AndEngine UIBadge: accent text on an accent * 0.15 background, sized by [sizeVariant].
 */
open class LegacyBadge : UIText() {

    var sizeVariant = SizeVariant.Medium
        set(value) {
            if (field != value) {
                field = value
                applySizes()
            }
        }

    init {
        wrapText = false
        style = Style { theme ->
            color = theme.palette.accent
            backgroundColor = theme.palette.accent * 0.15f
        }
        applySizes()
    }

    fun setText(@StringRes resId: Int) {
        text = StringTable.get(resId)
    }

    private fun applySizes() {
        val sizes = BadgeSizes(sizeVariant)
        fontSize = sizes.fontSize.vu.px
        padding = sizes.padding
        radius = sizes.radius.vu.px
    }
}

/**
 * The AndEngine UILabeledBadge: a label on a black 0.1 background next to its value, both accent on
 * an accent * 0.15 background, sized by [sizeVariant].
 */
open class LegacyLabeledBadge : UILinearContainer() {

    val labelEntity = UIText().apply {
        alignment = Anchor.Center
        wrapText = false
        backgroundColor = Color4(0f, 0f, 0f, 0.1f)
    }

    val valueEntity = UIText().apply {
        alignment = Anchor.Center
        wrapText = false
    }

    var label by labelEntity::text

    var value by valueEntity::text

    var sizeVariant = SizeVariant.Medium
        set(value) {
            if (field != value) {
                field = value
                applySizes()
            }
        }

    init {
        orientation = Orientation.Horizontal

        // Verktex only passes the alpha down, so the texts get the accent themselves.
        style = Style { theme ->
            backgroundColor = theme.palette.accent * 0.15f
            labelEntity.color = theme.palette.accent
            valueEntity.color = theme.palette.accent
        }

        this += labelEntity
        this += valueEntity

        applySizes()
    }

    private fun applySizes() {
        val sizes = BadgeSizes(sizeVariant)

        labelEntity.fontSize = sizes.fontSize.vu.px
        valueEntity.fontSize = sizes.fontSize.vu.px
        labelEntity.padding = sizes.padding
        valueEntity.padding = sizes.padding
        radius = sizes.radius.vu.px
        // The label background took the corner radius of the badge.
        labelEntity.radius = radius
    }
}
