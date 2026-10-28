package com.osudroid.verktex.bridge

import com.osudroid.ui.verktex.PersistentOverlays
import com.reco1l.verktex.Verktex
import com.reco1l.verktex.ui.UIComponent
import com.reco1l.verktex.ui.UIScene
import org.andengine.entity.scene.Scene
import org.andengine.entity.scene.background.Background
import ru.nsu.ccfit.zuev.osu.GlobalManager

/**
 * Where the screen is shown: an AndEngine scene or a Verktex scene.
 */
sealed class Destination {
    data class AndEngine(val scene: Scene) : Destination()
    data class Verktex(val scene: UIScene) : Destination()
}

/** A Verktex scene that wants to know when the router made it, or stopped it being, the shown scene. */
interface RoutedScene {
    fun onShown()

    /** The scene stopped being Verktex's scene: another Verktex scene, AndEngine, or [EngineRouter.leaveVerktex]. */
    fun onHidden() {}
}

/**
 * The only switch between AndEngine and Verktex. Verktex is kept at its base scene whenever
 * AndEngine owns the screen; `UIEngine.setScene` calls [leaveVerktex] to guarantee that.
 *
 * Components passed to [showOverlay] are tracked overlays: they make Verktex active over an
 * AndEngine scene, and every scene change ([leaveVerktex], [showVerktex]) hides them. Verktex
 * modals add themselves through `Verktex.addOverlay` and belong to their scene, so the router
 * neither tracks nor touches them.
 */
object EngineRouter {

    /**
     * Sets the active AndEngine scene. Replaceable for tests.
     */
    @JvmStatic
    var andEngineSceneSetter: (Scene) -> Unit = { GlobalManager.getInstance().engine.scene = it }

    /**
     * The scene AndEngine shows while Verktex owns the screen.
     */
    @JvmStatic
    val emptyAndEngineScene: Scene by lazy {
        Scene().apply { background = Background(0f, 0f, 0f) }
    }

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
        is Destination.AndEngine -> showAndEngine(destination.scene)
        is Destination.Verktex -> showVerktex(destination.scene)
    }

    @JvmStatic
    fun showVerktex(scene: UIScene) = showVerktex(scene, emptyAndEngineScene)

    /**
     * Like [showVerktex], but AndEngine shows [backdrop] under the Verktex scene instead of the empty
     * scene.
     */
    @JvmStatic
    fun showVerktex(scene: UIScene, backdrop: Scene) {
        andEngineSceneSetter(backdrop)

        Verktex.withTreeLock {
            hideTrackedOverlays()
            popToBase()?.onHidden()
            Verktex.pushScene(scene)
            (scene as? RoutedScene)?.onShown()
        }
    }

    @JvmStatic
    fun showAndEngine(scene: Scene) {
        leaveVerktex()
        andEngineSceneSetter(scene)
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
