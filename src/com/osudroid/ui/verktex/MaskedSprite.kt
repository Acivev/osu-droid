package com.osudroid.ui.verktex

import com.reco1l.verktex.Platform
import com.reco1l.verktex.Verktex
import com.reco1l.verktex.data.Vec2
import com.reco1l.verktex.data.Vec4
import com.reco1l.verktex.graphics.GraphicsRenderer
import com.reco1l.verktex.graphics.GraphicsShader
import com.reco1l.verktex.time.TickingClock
import com.reco1l.verktex.ui.UISprite
import kotlin.math.max

/**
 * The AndEngine UIShapedSprite with a rounded box shape: a cropped sprite masked to a box with
 * [cornerRadius]. The Verktex texture shader rounds its corners in texture space, which is wrong
 * once the sprite is cropped, so the mask is computed over the drawn part of the texture.
 */
open class MaskedSprite : UISprite() {

    /**
     * The corner radius of the mask, in pixels.
     */
    var cornerRadius = 0f

    init {
        scaleType = ScaleType.Crop
    }

    override fun onUpdate(clock: TickingClock) {
        createShader()
        super.onUpdate(clock)
    }

    override fun onDraw(renderer: GraphicsRenderer) {
        val shader = maskShader ?: return super.onDraw(renderer)
        val region = textureRegion ?: return

        if (scaleType != ScaleType.Crop || region.width <= 0f || region.height <= 0f) {
            return super.onDraw(renderer)
        }

        val width = bounds.width
        val height = bounds.height

        // The part of the texture UISprite shows when cropping.
        val scale = max(width / region.width, height / region.height)
        val visibleX = width / (region.width * scale)
        val visibleY = height / (region.height * scale)

        val u0 = region.u0 + (region.u1 - region.u0) * (1f - visibleX) * gravity.x
        val u1 = u0 + (region.u1 - region.u0) * visibleX
        val v0 = region.v0 + (region.v1 - region.v0) * (1f - visibleY) * gravity.y
        val v1 = v0 + (region.v1 - region.v0) * visibleY

        // drawTexture() would replace the uniform set, so the quad is emitted directly.
        renderer.setState(
            shader = shader,
            texture = region.texture,
            uniformSet = GraphicsShader.UniformSet()
                .set(UNIFORM_FRAME_RECT, Vec4(u0, v0, u1, v1))
                .set(UNIFORM_SIZE, Vec2(width, height))
                .set(UNIFORM_RADIUS, cornerRadius)
        )

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
        private const val UNIFORM_FRAME_RECT = "uFrameRect"
        private const val UNIFORM_SIZE = "uSize"
        private const val UNIFORM_RADIUS = "uRadius"

        private const val VERTEX_SHADER = "verktex/shaders/main.vert"
        private const val FRAGMENT_SHADER = "shaders/masked_sprite.frag"

        /** Shared by every masked sprite, null before the first update or when the source is missing. */
        private var maskShader: GraphicsShader? = null
        private var isShaderRequested = false

        /** Creates the shader once, on the update thread. Its GL program is built lazily when first bound. */
        private fun createShader() {
            if (isShaderRequested) return
            isShaderRequested = true
            maskShader = Platform.provider.createShaderSource(VERTEX_SHADER, FRAGMENT_SHADER, false)
                ?.let(Verktex.graphics::createShader)
        }
    }
}
