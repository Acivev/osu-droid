package com.osudroid.ui.verktex

import androidx.annotation.StringRes
import com.osudroid.ui.verktex.LegacyUnits.vu
import com.reco1l.andengine.ui.SizeVariant
import com.reco1l.verktex.data.Anchor
import com.reco1l.verktex.data.Color4
import com.reco1l.verktex.data.Padding
import com.reco1l.verktex.data.px
import com.reco1l.verktex.graphics.GraphicsRenderer
import com.reco1l.verktex.graphics.GraphicsTexture
import com.reco1l.verktex.time.TickingClock
import com.reco1l.verktex.ui.container.UIContainer
import com.reco1l.verktex.ui.container.UILinearContainer
import com.reco1l.verktex.ui.text.UIText
import ru.nsu.ccfit.zuev.osu.helper.StringTable
import kotlin.math.min

/**
 * The AndEngine `setText(resId)` of texts.
 */
fun UIText.setText(@StringRes resId: Int) {
    text = StringTable.get(resId)
}

inline fun UIContainer.legacyBadge(builder: LegacyBadge.() -> Unit) = LegacyBadge().apply(builder).also { +it }

inline fun UIContainer.legacyLabeledBadge(builder: LegacyLabeledBadge.() -> Unit) = LegacyLabeledBadge().apply(builder).also { +it }

/**
 * The font, padding, icon spacing and corner radius of an AndEngine badge size variant, in virtual units.
 */
private class BadgeSizes(variant: SizeVariant) {
    val fontSize: Float
    val padding: Padding
    val horizontal: Float
    val spacing: Float
    val radius: Float

    init {
        val font: String
        val vertical: Float

        when (variant) {
            SizeVariant.Small -> { font = "xs"; horizontal = 8f; vertical = 4f; spacing = 4f; radius = 6f }
            SizeVariant.Medium -> { font = "smallFont"; horizontal = 12f; vertical = 8f; spacing = 8f; radius = 12f }
            SizeVariant.Large -> { font = "font"; horizontal = 16f; vertical = 12f; spacing = 12f; radius = 16f }
        }

        fontSize = LegacyUnits.fontSize(font)
        padding = Padding(horizontal.vu.px, vertical.vu.px)
    }
}

/**
 * The AndEngine UIBadge: accent text on an accent * 0.15 background, sized by [sizeVariant], with an
 * optional leading texture icon.
 */
open class LegacyBadge : UIText() {

    // Verktex's leading icon is a font glyph, so a texture icon is drawn in the left padding.
    private var iconName: String? = null
    private var iconAssetPath: String? = null
    private var loadIcon: (name: String, assetPath: String) -> GraphicsTexture.Region? = SkinTextures::get
    private var icon: GraphicsTexture.Region? = null
    private var isIconInvalid = false
    private var iconSize = 0f
    private var horizontalPadding = 0f

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

    /**
     * Sets the leading icon, sized to the text height like the AndEngine CompoundText did. It is
     * looked up on the next update.
     */
    fun setLeadingIcon(name: String, assetPath: String, loadIcon: (name: String, assetPath: String) -> GraphicsTexture.Region? = SkinTextures::get) {
        iconName = name
        iconAssetPath = assetPath
        this.loadIcon = loadIcon
        isIconInvalid = true
    }

    override fun onUpdate(clock: TickingClock) {
        if (isIconInvalid) {
            isIconInvalid = false
            icon = iconName?.let { loadIcon(it, iconAssetPath!!) }

            val sizes = BadgeSizes(sizeVariant)
            iconSize = fontFamily[fontSize, fontWeight, fontStyle].metrics.lineHeight
            paddingLeft = (sizes.horizontal.vu + iconSize + sizes.spacing.vu).px
        }

        super.onUpdate(clock)
    }

    override fun onDraw(renderer: GraphicsRenderer) {
        super.onDraw(renderer)

        val icon = icon ?: return
        if (icon.width <= 0f || icon.height <= 0f) return

        // Fit inside a square of the text height, like the AndEngine leading icon sprite.
        val scale = min(iconSize / icon.width, iconSize / icon.height)
        val width = icon.width * scale
        val height = icon.height * scale

        val contentTop = paddingTop.toPixels()
        val contentHeight = bounds.height - contentTop - paddingBottom.toPixels()

        renderer.drawTexture(
            texture = icon.texture,
            region = icon,
            x = horizontalPadding.vu + (iconSize - width) / 2f,
            y = contentTop + (contentHeight - height) / 2f,
            width = width,
            height = height,
            color = contentColor
        )
    }

    private fun applySizes() {
        val sizes = BadgeSizes(sizeVariant)
        fontSize = sizes.fontSize.vu.px
        padding = sizes.padding
        radius = sizes.radius.vu.px
        horizontalPadding = sizes.horizontal

        // The icon and its padding follow on the next update.
        if (iconName != null) {
            isIconInvalid = true
        }
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
