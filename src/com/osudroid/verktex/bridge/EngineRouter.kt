package com.osudroid.verktex.bridge

import com.osudroid.ui.verktex.PersistentOverlays
import com.reco1l.verktex.Verktex
import com.reco1l.verktex.ui.UIComponent
import com.reco1l.verktex.ui.UIScene

/**
 * Where the screen is shown: a Verktex scene.
 */
sealed class Destination {
    data class Verktex(val scene: UIScene) : Destination()
}

/** A Verktex scene that wants to know when the router made it, or stopped it being, the shown scene. */
interface RoutedScene {
    fun onShown()

    /** The scene stopped being Verktex's scene: another Verktex scene or [EngineRouter.leaveVerktex]. */
    fun onHidden() {}
}

/**
 * The switch between the shown Verktex scenes. [leaveVerktex] returns Verktex to its base scene.
 *
 * Components passed to [showOverlay] are tracked overlays: they make Verktex active over its base
 * scene, and every scene change ([leaveVerktex], [showVerktex]) hides them. Verktex modals add
 * themselves through `Verktex.addOverlay` and belong to their scene, so the router neither tracks
 * nor touches them.
 */
object EngineRouter {

    private val trackedOverlays = mutableSetOf<UIComponent>()

    @JvmStatic
    val isVerktexActive: Boolean
        get() = Verktex.scene !== UIScene.EmptyScene || hasShownOverlay

    @JvmStatic
    val hasShownOverlay: Boolean
        get() = Verktex.withTreeLock { trackedOverlays.isNotEmpty() }

    @JvmStatic
    fun isOverlayShown(component: UIComponent): Boolean =
        Verktex.withTreeLock { component in trackedOverlays }

    @JvmStatic
    fun showOverlay(component: UIComponent) = Verktex.withTreeLock {
        if (trackedOverlays.add(component)) {
            Verktex.addOverlay(component)
            // The FPS counter and the room chat stay above it, as they were on the AndEngine HUD.
            PersistentOverlays.bringToFront()
        }
    }

    @JvmStatic
    fun hideOverlay(component: UIComponent) = Verktex.withTreeLock {
        if (trackedOverlays.remove(component)) {
            Verktex.removeOverlay(component)
        }
    }

    @JvmStatic
    fun show(destination: Destination) = when (destination) {
        is Destination.Verktex -> showVerktex(destination.scene)
    }

    @JvmStatic
    fun showVerktex(scene: UIScene) {
        Verktex.withTreeLock {
            hideTrackedOverlays()
            popToBase()?.onHidden()
            Verktex.pushScene(scene)
            (scene as? RoutedScene)?.onShown()
        }
    }

    @JvmStatic
    fun leaveVerktex() {
        Verktex.withTreeLock {
            hideTrackedOverlays()
            popToBase()?.onHidden()
        }
    }

    private fun hideTrackedOverlays() {
        trackedOverlays.toList().forEach(::hideOverlay)
    }

    /** Pops every scene above the base; returns the previously shown scene when it is a [RoutedScene]. */
    private fun popToBase(): RoutedScene? {
        val shown = Verktex.scene as? RoutedScene
        while (Verktex.scene !== UIScene.EmptyScene) {
            Verktex.popScene()
        }
        return shown
    }
}
