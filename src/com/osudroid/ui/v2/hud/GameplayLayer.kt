package com.osudroid.ui.v2.hud

import com.osudroid.ui.verktex.FullWindowContainer
import com.osudroid.ui.verktex.SkinTextures
import com.reco1l.verktex.Verktex
import com.reco1l.verktex.graphics.GraphicsTexture
import com.reco1l.verktex.ui.UIComponent
import com.rian.andengine.modifier.ModifierRegistry
import ru.nsu.ccfit.zuev.osu.ResourceManager

/**
 * The Verktex layer that holds what the AndEngine camera HUD held during gameplay below the pause
 * menu: the [GameplayHUD] and the components `GameScene` builds into it.
 *
 * It is an untracked overlay ([Verktex.addOverlay]), so the router does not see it and the key path
 * keeps pausing and resuming the game. It consumes nothing on its own: a touch no child takes falls
 * through to the AndEngine scene.
 */
class GameplayLayer : FullWindowContainer() {

    /**
     * Whether the layer is shown as an overlay.
     */
    var isShown = false
        private set

    /**
     * The FPS counter overlay, kept above the layer while it is shown. Null while the counter is not a
     * Verktex overlay.
     */
    var fpsProvider: (() -> UIComponent?)? = null

    /**
     * The chat overlay when it is shown, kept above the layer and the FPS counter. Null while the chat
     * is not a Verktex overlay.
     */
    var chatProvider: (() -> UIComponent?)? = null

    private val topOverlays = mutableListOf<UIComponent>()


    /**
     * Adds the layer as an overlay. Verktex appends overlays, so the FPS counter and the shown chat are
     * added again above it, as they were above the AndEngine HUD.
     */
    fun show() = Verktex.withTreeLock {
        if (isShown) {
            return@withTreeLock
        }

        isShown = true
        Verktex.addOverlay(this)

        reAddAbove(fpsProvider?.invoke())
        reAddAbove(chatProvider?.invoke())
    }

    /**
     * Removes the layer and the top overlay (the pause or fail menu) if one is shown.
     */
    fun hide() = Verktex.withTreeLock {
        topOverlays.toList().forEach(::hideTopOverlay)

        if (!isShown) {
            return@withTreeLock
        }

        isShown = false
        Verktex.removeOverlay(this)
    }

    /**
     * Adds [component] as the overlay above everything else (the pause or fail menu).
     */
    fun showTopOverlay(component: UIComponent) = Verktex.withTreeLock {
        if (component !in topOverlays) {
            topOverlays += component
            Verktex.addOverlay(component)
        }
    }

    /**
     * Removes an overlay added with [showTopOverlay].
     */
    fun hideTopOverlay(component: UIComponent) = Verktex.withTreeLock {
        if (topOverlays.remove(component)) {
            Verktex.removeOverlay(component)
        }
    }

    /**
     * The AndEngine `hud.setIgnoreUpdate`: neither the HUD's update nor its modifiers run while paused.
     */
    fun setHudPaused(hud: GameplayHUD, paused: Boolean) = Verktex.withTreeLock {
        hud.ignoreUpdate = paused
        ModifierRegistry.setPaused(hud, paused)
    }

    // Only a shown overlay (one with a parent) is moved, a hidden one stays hidden.
    private fun reAddAbove(component: UIComponent?) {
        if (component?.parent != null) {
            Verktex.removeOverlay(component)
            Verktex.addOverlay(component)
        }
    }


    companion object {

        /**
         * The frames of an animated skin texture, the lookup of the AndEngine
         * `UIAnimatedSprite(textureName, withHyphen = true, ...)`: "<name>-<i>" for every frame the
         * skin has, else the texture itself. Must be called on the update thread.
         */
        @JvmStatic
        fun skinAnimationFrames(textureName: String): List<GraphicsTexture.Region> {
            val frames = mutableListOf<GraphicsTexture.Region>()
            val frameCount = ResourceManager.getInstance().getFrameCount(textureName)

            for (i in 0 until frameCount) {
                SkinTextures.getWithBeatmapSkin("$textureName-$i")?.let(frames::add)
            }

            if (frames.isEmpty()) {
                SkinTextures.getWithBeatmapSkin(textureName)?.let(frames::add)
            }

            return frames
        }
    }
}
