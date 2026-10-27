package com.osudroid.ui.v2.menu.main

import com.osudroid.ui.v2.menu.main.shader.CatJamCircleShader
import com.reco1l.verktex.graphics.GraphicsRenderer
import com.reco1l.verktex.graphics.GraphicsTexture
import com.reco1l.verktex.time.TickingClock
import com.reco1l.verktex.ui.UIAnimatedSprite

/**
 * A sprite that renders a catjam animation frame clipped to a circle.
 *
 * Uses [com.osudroid.ui.v2.menu.main.shader.CatJamCircleShader] which discards fragments outside a circular radius in
 * normalized quad-UV space (0→1). No mask texture is needed — the circle is computed
 * mathematically in the fragment shader.
 *
 * The sprite should be sized to match the logo exactly (same width/height) so that
 * the UV-space circle radius of 0.5 coincides with the logo's circle boundary.
 *
 * The frame is picked by the host from the song position with [showFrame], the animation does not
 * advance on its own.
 */
class KiaiCatJamSprite(frames: List<GraphicsTexture.Region> = emptyList()) : UIAnimatedSprite(frames) {

    /**
     * Circle clip radius in normalized quad-UV space [0..1].
     * 0.5 means the circle fills the full quad (touching all four sides at the midpoints).
     * Reduce slightly (e.g. 0.48) to add extra inset margin.
     */
    var clipRadius: Float = 0.5f

    /** The circle clip shader, created on the first update, on the renderer this sprite is drawn with. */
    private val circleShader = CatJamCircleShader()

    init {
        scaleType = ScaleType.Stretch
        alpha = 0f
        pause()
    }


    /** Shows the frame at [index] until the next call. */
    fun showFrame(index: Int) {
        pause()
        // Centred in the frame's time slot so the frame lookup does not round down to the previous one.
        elapsedSec = (index + 0.5f) * frameTime
        textureRegion = frames.getOrNull(index)
    }


    override fun onUpdate(clock: TickingClock) {
        circleShader.create()

        super.onUpdate(clock)
    }

    override fun onDraw(renderer: GraphicsRenderer) {
        val shader = circleShader.shader ?: return
        if (alpha <= 0f) return
        val region = textureRegion ?: return
        if (region.width <= 0f || region.height <= 0f) return

        val u0 = region.u0
        val v0 = region.v0
        val u1 = region.u1
        val v1 = region.v1

        // drawTexture() would replace the uniform set, so the quad is emitted directly.
        renderer.setState(
            shader = shader,
            texture = region.texture,
            uniformSet = circleShader.uniformSet(region, clipRadius)
        )

        val width = bounds.width
        val height = bounds.height

        renderer.drawGeometry { transform, _ ->
            val color = renderer.transformColor(contentColor)

            val (x0, y0) = transform.transform(0f, 0f)
            val (x1, y1) = transform.transform(width, 0f)
            val (x2, y2) = transform.transform(width, height)
            val (x3, y3) = transform.transform(0f, height)

            // addVertex is a member extension of the renderer, so it must be in scope.
            with(renderer) {
                addVertex(x0, y0, u0, v0, color)
                addVertex(x1, y1, u1, v0, color)
                addVertex(x2, y2, u1, v1, color)

                addVertex(x0, y0, u0, v0, color)
                addVertex(x2, y2, u1, v1, color)
                addVertex(x3, y3, u0, v1, color)
            }
        }
    }

    companion object {
        const val GRID_COLS = 13
        const val FRAME_SIZE = 112
        const val TOTAL_FRAMES = 143
    }
}