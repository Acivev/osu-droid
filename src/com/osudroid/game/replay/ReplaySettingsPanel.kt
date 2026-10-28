package com.osudroid.game.replay

import com.osudroid.math.Precision
import com.osudroid.ui.verktex.LegacyUnits
import com.osudroid.ui.verktex.LegacyUnits.vu
import com.reco1l.verktex.data.Anchor
import com.reco1l.verktex.data.Axis
import com.reco1l.verktex.data.Color4
import com.reco1l.verktex.data.Padding
import com.reco1l.verktex.data.fill
import com.reco1l.verktex.data.px
import com.reco1l.verktex.input.InputEvent
import com.reco1l.verktex.input.PointerEvent
import com.reco1l.verktex.time.TickingClock
import com.reco1l.verktex.ui.container.UIContainer
import com.reco1l.verktex.ui.container.UILinearContainer
import com.reco1l.verktex.ui.container.UILinearContainer.Orientation
import com.reco1l.verktex.ui.container.UIScrollableContainer
import com.reco1l.verktex.ui.container.linearContainer
import com.reco1l.verktex.ui.text.text
import com.rian.andengine.modifier.clearModifiers
import com.rian.andengine.modifier.fadeIn
import com.rian.andengine.modifier.fadeTo
import com.rian.andengine.modifier.modifierTarget
import com.rian.andengine.modifier.moveToX

/**
 * A panel that contains settings for replay playback. Can be expanded and collapsed by tapping the button on the left.
 */
class ReplaySettingsPanel : UIContainer() {
    lateinit var playbackControl: ReplayPlaybackControl
        private set

    lateinit var visualSettingsControl: ReplayVisualSettingsControl
        private set

    private val isExpanded
        get() = Precision.almostEquals(modifierTarget.getX(), 0f)

    private val elementContainer = UIScrollableContainer().apply {
        x = BUTTON_WIDTH.vu.px

        scrollAxes = Axis.Y
        width = PANEL_WIDTH.vu.px
        height = fill()
        showVerticalIndicator = false

        linearContainer {
            width = fill()
            spacing = 20f.vu.px
            padding = Padding(0f.px, 20f.vu.px)
            orientation = Orientation.Vertical

            addControls()
        }
    }

    // The AndEngine onLoadComplete ran on the first update after an attach.
    private var isLoaded = false

    init {
        x = PANEL_WIDTH.vu.px
        height = fill()
        anchor = Anchor.TopRight
        origin = Anchor.TopRight
        alpha = 0f
        elementContainer.isVisible = false

        // The gameplay layer runs on the engine's update clock, the AndEngine panel had to be given it.

        +ReplaySettingsPanelButton()
        +elementContainer
    }

    override fun onUpdate(clock: TickingClock) {
        if (!isLoaded) {
            isLoaded = true
            onLoadComplete()
        }

        super.onUpdate(clock)
    }

    override fun onDetached() {
        isLoaded = false
        super.onDetached()
    }

    private fun onLoadComplete() {
        fadeTo(0.5f, 0.2f)
    }

    fun expand() {
        if (isExpanded) {
            return
        }

        elementContainer.isVisible = true

        clearModifiers()
        moveToX(0f, 0.2f)
        fadeIn(0.2f)
    }

    fun collapse() {
        if (!isExpanded) {
            return
        }

        clearModifiers()
        moveToX(PANEL_WIDTH.vu, 0.2f)
        fadeTo(0.5f, 0.2f).after { elementContainer.isVisible = false }
    }

    private fun UILinearContainer.addControls() {
        playbackControl = ReplayPlaybackControl()
        +playbackControl

        visualSettingsControl = ReplayVisualSettingsControl()
        +visualSettingsControl
    }

    private inner class ReplaySettingsPanelButton : UIContainer() {

        // Verktex offers moves and releases to every child, AndEngine bound them to the pressed area.
        private var pressedPointerId = -1

        init {
            backgroundColor = Color4(0xFF181825)
            radius = BUTTON_RADIUS.vu.px

            width = BUTTON_WIDTH.vu.px
            height = 150f.vu.px
            y = (-125f).vu.px

            anchor = Anchor.CenterLeft
            origin = Anchor.CenterLeft

            text {
                rotationZ = -90f
                anchor = Anchor.Center
                origin = Anchor.Center
                fontSize = LegacyUnits.fontSize("smallFont").vu.px
                wrapText = false
                text = "Settings"
            }
        }

        override fun onInputEvent(event: InputEvent) = when {
            event !is PointerEvent -> false

            event.action == PointerEvent.Action.Down -> {
                pressedPointerId = event.pointerId
                true
            }

            event.pointerId != pressedPointerId -> false

            event.action == PointerEvent.Action.Move -> true

            event.action == PointerEvent.Action.Up -> {
                pressedPointerId = -1

                if (isExpanded) {
                    collapse()
                } else {
                    expand()
                }

                false
            }

            else -> {
                pressedPointerId = -1
                false
            }
        }
    }

    companion object {
        private const val PANEL_WIDTH = 440f
        private const val BUTTON_WIDTH = 48f
        private const val BUTTON_RADIUS = 12f
    }
}
