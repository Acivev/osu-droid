package com.osudroid.ui.verktex

import com.osudroid.ui.verktex.LegacyUnits.vu
import com.reco1l.verktex.data.Anchor
import com.reco1l.verktex.data.ClipMode
import com.reco1l.verktex.data.Padding
import com.reco1l.verktex.data.fill
import com.reco1l.verktex.data.px
import com.reco1l.verktex.data.wrap
import com.reco1l.verktex.math.Easing
import com.reco1l.verktex.math.FloatAnimation
import com.reco1l.verktex.time.TickingClock
import com.reco1l.verktex.ui.container.UIClickableContainer
import com.reco1l.verktex.ui.container.UIContainer
import com.reco1l.verktex.ui.container.UILinearContainer
import com.reco1l.verktex.ui.shape.UIBox
import com.reco1l.verktex.ui.shape.UITriangle
import com.reco1l.verktex.ui.text.UIText

fun UIContainer.collapsibleCard(builder: CollapsibleCard.() -> Unit) = CollapsibleCard().apply(builder).also { +it }

/**
 * The AndEngine UICard: a card with a title bar that collapses or expands the [content] when
 * tapped, its height and the triangle animated linearly over 0.1 s.
 */
open class CollapsibleCard : UILinearContainer() {

    /**
     * The content of the card.
     */
    val content = UILinearContainer().apply {
        orientation = Orientation.Vertical
        width = fill()
        clipping = ClipMode.Bounds
    }

    private val titleText = UIText().apply {
        fontSize = LegacyUnits.fontSize("smallFont").vu.px
        wrapText = false
        anchor = Anchor.CenterLeft
        origin = Anchor.CenterLeft
        style = Style { theme -> color = theme.palette.accent }
    }

    private val triangle = UITriangle().apply {
        anchor = Anchor.CenterRight
        origin = Anchor.CenterRight
        rotationOrigin = Anchor.Center
        width = 16f.vu.px
        height = 12f.vu.px
        style = Style { theme ->
            color = theme.palette.accent
            alpha = 0.5f
        }
    }

    /**
     * The title of the card.
     */
    var title by titleText::text

    /**
     * A function that is called when this card is collapsed or expanded.
     */
    var onExpandStatusChange: ((isExpanded: Boolean) -> Unit)? = null

    // The content height as a fraction of its own, and the triangle rotation, as heightTo and rotateTo animated them.
    private val contentHeight = FloatAnimation(1f, 0.1f, Easing.None)
    private val triangleRotation = FloatAnimation(0f, 0.1f, Easing.None)

    init {
        orientation = Orientation.Vertical
        radius = 14f.vu.px
        style = Style { theme -> backgroundColor = theme.palette.accent * 0.15f }

        +UIClickableContainer().apply {
            width = fill()
            padding = Padding(12f.vu.px, 8f.vu.px)

            onActionUp = {
                if (content.isVisible) {
                    collapse()
                } else {
                    expand()
                }
            }

            +titleText
            +triangle
        }

        +UIBox().apply {
            width = fill()
            height = 1f.vu.px
            style = Style { theme ->
                backgroundColor = theme.palette.accent
                alpha = 0.025f
            }
        }

        +content
    }

    /**
     * Collapses the card content.
     *
     * @param immediate Whether to collapse immediately (without animation).
     */
    @JvmOverloads
    fun collapse(immediate: Boolean = false) {
        if (content.isVisible) {
            animate(triangleRotation, 180f, immediate)
            animate(contentHeight, 0f, immediate)

            onExpandStatusChange?.invoke(false)
        }
    }

    /**
     * Expands the card content.
     *
     * @param immediate Whether to expand immediately (without animation).
     */
    @JvmOverloads
    fun expand(immediate: Boolean = false) {
        if (!content.isVisible) {
            animate(triangleRotation, 0f, immediate)

            content.isVisible = true
            animate(contentHeight, 1f, immediate)

            onExpandStatusChange?.invoke(true)
        }
    }

    private fun animate(animation: FloatAnimation, target: Float, immediate: Boolean) {
        if (immediate) {
            animation.jumpTo(target)
        } else {
            animation.animateTo(target, 0.1f, Easing.None)
        }
    }

    override fun onUpdate(clock: TickingClock) {
        val height = contentHeight.update(clock)
        content.height = wrap(height)

        // The content is hidden once it is collapsed, as heightTo(0).after {} did.
        if (height == 0f && contentHeight.target == 0f) {
            content.isVisible = false
        }

        // The Verktex triangle points down, the AndEngine one pointed up.
        triangle.rotationZ = triangleRotation.update(clock) + 180f

        super.onUpdate(clock)
    }
}
