package com.osudroid.ui.verktex

import com.osudroid.multiplayer.Multiplayer
import com.osudroid.ui.FPSCounter
import com.reco1l.verktex.Verktex
import com.reco1l.verktex.ui.UIComponent
import com.reco1l.verktex.ui.container.UIContainer

/**
 * The FPS counter and the shown room chat were on the AndEngine camera HUD, drawn above every screen.
 * As Verktex overlays they are kept above an overlay added later by moving them to the front.
 */
object PersistentOverlays {

    /**
     * The FPS counter's overlay root. A seam for the tests.
     */
    var fpsRoot: () -> UIComponent? = { FPSCounter.root }

    /**
     * The room chat. A seam for the tests.
     */
    var chat: () -> UIComponent? = { Multiplayer.roomScene?.chat }

    /**
     * Moves the FPS counter and then the chat to the front of their parent, each only when it is shown
     * (has a parent). They are reordered in place, never removed and added again: detaching the chat
     * would collapse it.
     */
    @JvmStatic
    fun bringToFront() = Verktex.withTreeLock {
        moveToFront(fpsRoot())
        moveToFront(chat())
    }

    private fun moveToFront(component: UIComponent?) {
        val parent = component?.parent as? UIContainer ?: return
        parent.run { component.moveToFront() }
    }
}
