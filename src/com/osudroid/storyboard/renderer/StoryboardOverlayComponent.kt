package com.osudroid.storyboard.renderer

import com.osudroid.storyboard.model.StoryboardLayerType
import com.reco1l.verktex.data.px
import com.reco1l.verktex.graphics.GraphicsRenderer
import com.reco1l.verktex.ui.UIComponent
import ru.nsu.ccfit.zuev.osu.Config

/**
 * Renders the `Overlay` layer of a storyboard, meant to be attached above gameplay elements.
 *
 * The playback state, texture pool and batch are shared with the owning [StoryboardComponent].
 * The background dim is applied by darkening the sprites rather than by covering them, as this
 * layer is displayed in front of gameplay elements.
 */
class StoryboardOverlayComponent internal constructor(
    private val storyboardComponent: StoryboardComponent
) : UIComponent() {

    init {
        width = Config.getRES_WIDTH().toFloat().px
        height = Config.getRES_HEIGHT().toFloat().px
    }

    /**
     * Whether this component is attached, as the AndEngine entity's `hasParent()`.
     */
    fun hasParent() = parent != null

    // The component's alpha reaches the quads through the renderer's base color, see StoryboardComponent.onDraw.
    override fun onDraw(renderer: GraphicsRenderer) {
        val playback = storyboardComponent.playback ?: return
        val batch = storyboardComponent.batch

        batch.begin(renderer)
        storyboardComponent.drawLayers(playback, OVERLAY_LAYERS, 1f, storyboardComponent.brightness)
        batch.end()
    }

    companion object {
        private val OVERLAY_LAYERS = arrayOf(StoryboardLayerType.Overlay)
    }
}
