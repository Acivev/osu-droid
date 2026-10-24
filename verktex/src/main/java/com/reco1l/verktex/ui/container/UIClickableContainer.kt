package com.reco1l.verktex.ui.container

import com.reco1l.verktex.input.InputEvent
import com.reco1l.verktex.input.PointerEvent
import com.reco1l.verktex.time.TickingClock


inline fun UIContainer.clickableContainer(builder: UIClickableContainer.() -> Unit) = UIClickableContainer().apply(builder).also { +it }

open class UIClickableContainer : UIContainer() {

    /**
     * Whether the button is being pressed or not.
     */
    var isPressed = false
        private set

    //region Events

    /**
     * The action to perform when the button is pressed.
     */
    var onActionDown: (() -> Unit)? = null

    /**
     * The action to perform when the button is released.
     */
    var onActionUp: (() -> Unit)? = null

    /**
     * The action to perform when the button is cancelled.
     */
    var onActionCancel: (() -> Unit)? = null

    /**
     * The action to perform when the button is long pressed.
     */
    var onActionLongPress: (() -> Unit)? = null

    //endregion

    private var pressStartTime = 0L

    // The finger pressing this container, others must not release or cancel it.
    private var pressPointerId = -1


    override fun onInputEvent(event: InputEvent): Boolean {

        if (super.onInputEvent(event)) {
            return true
        }

        // Other events (e.g. keys) are not handled by clickable containers.
        if (event !is PointerEvent) {
            return false
        }

        if (event.action != PointerEvent.Action.Down && (!isPressed || event.pointerId != pressPointerId)) {
            return false
        }

        return when (event.action) {
            PointerEvent.Action.Down -> {
                // Already held by another finger.
                if (isPressed) return true

                isPressed = true
                pressPointerId = event.pointerId
                onActionDown?.invoke()
                pressStartTime = System.currentTimeMillis()
                true
            }

            PointerEvent.Action.Up -> {
                if (containsScenePoint(event.x, event.y)) {
                    onActionUp?.invoke()
                } else {
                    onActionCancel?.invoke()
                }
                isPressed = false
                true
            }

            PointerEvent.Action.Move -> true

            // Cancel events are dispatched to every component, so they must not be consumed.
            PointerEvent.Action.Unknown -> {
                onActionCancel?.invoke()
                isPressed = false
                false
            }
        }
    }

    override fun onUpdate(clock: TickingClock) {
        if (onActionLongPress != null) {
            if (isPressed && System.currentTimeMillis() - pressStartTime >= 500L) {
                isPressed = false
                onActionLongPress?.invoke()
            }
        }
        super.onUpdate(clock)
    }

}