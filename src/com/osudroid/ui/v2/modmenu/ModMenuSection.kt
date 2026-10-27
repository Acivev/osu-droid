package com.osudroid.ui.v2.modmenu

import com.osudroid.ui.verktex.LegacyUnits
import com.osudroid.ui.verktex.LegacyUnits.vu
import com.osudroid.ui.verktex.times
import com.reco1l.verktex.data.Anchor
import com.reco1l.verktex.data.Axis
import com.reco1l.verktex.data.ClipMode
import com.reco1l.verktex.data.Padding
import com.reco1l.verktex.data.fill
import com.reco1l.verktex.data.px
import com.reco1l.verktex.time.TickingClock
import com.reco1l.verktex.ui.UIButton
import com.reco1l.verktex.ui.container.UILinearContainer
import com.reco1l.verktex.ui.container.UIScrollableContainer
import com.reco1l.verktex.ui.text.UIText

@Suppress("LeakingThis")
open class ModMenuSection(name: String, private val toggles: List<UIButton> = listOf()) : UILinearContainer() {

    protected val toggleContainer: UILinearContainer

    private val titleText = UIText().apply {
        width = fill()
        text = name
        alignment = Anchor.Center
        wrapText = false
        style = Style { theme ->
            color = theme.palette.accent.copy(alpha = 0.75f)
        }
    }

    /**
     * The width of this section in virtual units.
     */
    protected open val sectionWidth = 340f

    // LegacyUnits.scale the sizes were last applied for.
    private var appliedScale = 0f


    init {
        orientation = Orientation.Vertical
        height = fill()

        style = Style { theme ->
            backgroundColor = theme.palette.accent * 0.1f
        }

        +titleText

        +UIScrollableContainer().apply {
            scrollAxes = Axis.Y
            width = fill()
            height = fill()
            clipping = ClipMode.Bounds

            +VisibleChildrenContainer().apply {
                orientation = Orientation.Vertical
                width = fill()
                toggleContainer = this

                toggles.forEach { +it }
            }
        }

        updateVisibility()
    }

    fun updateVisibility() {
        isVisible = toggles.any { it.isVisible }
    }

    open fun onSearchTermUpdate(searchTerm: String) {
        // Not using updateVisibility() here to avoid iterating over the toggles twice.
        var anyVisible = false

        toggles.forEach {
            if (it is ModMenuToggle) {
                it.updateVisibility(searchTerm)
            }

            if (it.isVisible) {
                anyVisible = true
            }
        }

        isVisible = anyVisible
    }

    override fun onUpdate(clock: TickingClock) {
        if (appliedScale != LegacyUnits.scale) {
            appliedScale = LegacyUnits.scale

            width = sectionWidth.vu.px
            radius = 16f.vu.px
            titleText.fontSize = LegacyUnits.fontSize("smallFont").vu.px
            titleText.padding = Padding(12f.vu.px)
            toggleContainer.padding = Padding(12f.vu.px, 0f.px, 12f.vu.px, 12f.vu.px)
            toggleContainer.spacing = 16f.vu.px
        }

        super.onUpdate(clock)
    }

}
