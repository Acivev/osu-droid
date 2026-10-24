package com.reco1l.verktex.ui.control

import com.reco1l.verktex.*
import com.reco1l.verktex.input.InputEvent
import com.reco1l.verktex.input.PointerEvent
import com.reco1l.verktex.data.Anchor
import com.reco1l.verktex.math.interpolateColor
import com.reco1l.verktex.math.interpolateFloat
import com.reco1l.verktex.ui.FAIcon
import com.reco1l.verktex.ui.UIIcon
import com.reco1l.verktex.time.TickingClock

class UICheckbox(initialValue: Boolean = false) : UIControl<Boolean>(initialValue) {

    private val checkIcon = UIIcon(FAIcon.Check).apply {
        anchor = Anchor.Center
        origin = Anchor.Center
        style = Style {
            color = it.palette.accent
        }

        if (!initialValue) {
            scaleX = 0f
            scaleY = 0f
            alpha = 0f
        }
    }


    private var isPressed = false


    init {
        style = Style { theme ->
            width = theme.controlStyle.minimumHeight
            height = theme.controlStyle.minimumHeight
            backgroundColor = if (value) theme.palette.controlActiveBackground else theme.palette.controlInactiveBackground
            radius = theme.radius.lg
        }
        +checkIcon
    }


    override fun onUpdate(clock: TickingClock) {

        val targetScaleAndAlpha = if (value) 1f else 0f
        val targetBackgroundCOlor = if (value) Verktex.theme.palette.controlActiveBackground else Verktex.theme.palette.controlInactiveBackground

        checkIcon.scale = clock.interpolateFloat(0.2f, checkIcon.scale, targetScaleAndAlpha)
        checkIcon.alpha = clock.interpolateFloat(0.2f, checkIcon.alpha, targetScaleAndAlpha)
        backgroundColor = clock.interpolateColor(0.2f, backgroundColor, targetBackgroundCOlor)

        super.onUpdate(clock)
    }


    override fun onInputEvent(event: InputEvent): Boolean {
        if (event is PointerEvent) {
            // Up and cancel events are dispatched to every component, only the pressed one handles them.
            return when (event.action) {
                PointerEvent.Action.Down -> {
                    isPressed = true
                    true
                }
                PointerEvent.Action.Up -> {
                    if (!isPressed) return false
                    isPressed = false
                    if (containsScenePoint(event.x, event.y)) {
                        value = !value
                    }
                    true
                }
                PointerEvent.Action.Unknown -> {
                    isPressed = false
                    false
                }
                else -> isPressed
            }
        }
        return super.onInputEvent(event)
    }
}