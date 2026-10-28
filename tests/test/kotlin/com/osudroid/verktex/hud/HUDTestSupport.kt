package com.osudroid.verktex.hud

import com.reco1l.verktex.data.LayoutConstraints
import com.reco1l.verktex.data.Vec4
import com.reco1l.verktex.data.fill
import com.reco1l.verktex.input.InputEvent
import com.reco1l.verktex.input.PointerEvent
import com.reco1l.verktex.ui.UIComponent
import com.reco1l.verktex.ui.container.UIContainer

internal const val WINDOW_WIDTH = 1280f
internal const val WINDOW_HEIGHT = 720f

/**
 * Lays [component] out over the fake window, the way the overlay scene does when it draws.
 */
internal fun layoutOverWindow(component: UIComponent) {
    component.measure(LayoutConstraints(0f, WINDOW_WIDTH, 0f, WINDOW_HEIGHT))
    component.layout(Vec4(0f, 0f, WINDOW_WIDTH, WINDOW_HEIGHT))
}

/**
 * A window-filling component that writes its [name] into [log] for every press it receives.
 */
internal class PressRecorder(
    private val name: String,
    private val log: MutableList<String>,
    private val consume: Boolean = false
) : UIContainer() {

    init {
        width = fill()
        height = fill()
    }

    override fun onInputEvent(event: InputEvent): Boolean {
        if (event is PointerEvent && event.action == PointerEvent.Action.Down) {
            log += name
        }
        return consume
    }
}
