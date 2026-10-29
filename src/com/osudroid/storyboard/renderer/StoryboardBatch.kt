package com.osudroid.storyboard.renderer

import com.osudroid.storyboard.playback.PlayableSprite
import com.reco1l.verktex.BuiltIn
import com.reco1l.verktex.data.Color4
import com.reco1l.verktex.graphics.BlendMode
import com.reco1l.verktex.graphics.GraphicsRenderer
import com.reco1l.verktex.graphics.GraphicsShader
import com.reco1l.verktex.graphics.GraphicsTexture
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

/**
 * A quad batch for storyboard rendering.
 *
 * Quads are accumulated into a client-side vertex array with the layout
 * `[x, y, u, v, r, g, b, a]` per vertex and flushed into the [GraphicsRenderer] as two triangles
 * per quad whenever the texture or blend mode changes, or the batch is full. The renderer merges
 * consecutive flushes of the same texture and blend mode into one draw command.
 *
 * Storyboard textures are uploaded with straight (non-premultiplied) alpha
 * ([StoryboardTextureSource] un-premultiplies on upload through
 * [com.reco1l.verktex.android.AndroidTextureSource], as AndEngine's `BILINEAR` upload did), so
 * vertex colors are straight as well and normal blending uses
 * `(SRC_ALPHA, ONE_MINUS_SRC_ALPHA)` while additive blending uses `(SRC_ALPHA, ONE)` - matching
 * osu!stable and the previous renderer, which selected these factors based on the texture's
 * premultiply flag. Getting this wrong burns glow textures (colored white with the shape in the
 * alpha channel) into solid rectangles.
 *
 * The renderer does not transform geometry for the batch: every vertex is put through the
 * renderer's current matrix (so the storyboard follows the playfield scale and offset) and every
 * alpha is multiplied by its base color alpha (the alpha of the drawing component and its parents).
 */
class StoryboardBatch {
    private val vertices = FloatArray(MAX_QUADS * FLOATS_PER_QUAD)

    // The quads are drawn with the built-in texture quad shader, which multiplies the vertex color
    // with the sample. An explicit empty set, the renderer would keep the last draw's uniforms
    // (such as a rounded corner radius) otherwise.
    private val uniforms = GraphicsShader.UniformSet()

    // Transformed corner positions of the quad being flushed.
    private val points = FloatArray(8)

    private var offset = 0
    private var texture: GraphicsTexture? = null
    private var additiveBlend = false
    private var renderer: GraphicsRenderer? = null

    // The transform from storyboard space into the current component space, applied on the CPU so
    // that all layers can share one batch regardless of the letterbox mapping.
    private var spaceOffsetX = 0f
    private var spaceOffsetY = 0f
    private var spaceScale = 1f

    /**
     * Begins a batch. Must be called from the draw thread before any quads are added.
     *
     * @param renderer The [GraphicsRenderer] to draw into.
     */
    fun begin(renderer: GraphicsRenderer) {
        this.renderer = renderer
        offset = 0
        texture = null
        additiveBlend = false
        resetSpace()
    }

    /**
     * Sets the storyboard-space transform applied to subsequently added quads.
     *
     * @param offsetX The X offset in component space.
     * @param offsetY The Y offset in component space.
     * @param scale The scale from storyboard units to component units.
     */
    fun setSpace(offsetX: Float, offsetY: Float, scale: Float) {
        this.spaceOffsetX = offsetX
        this.spaceOffsetY = offsetY
        this.spaceScale = scale
    }

    /**
     * Resets the storyboard-space transform to identity.
     */
    fun resetSpace() = setSpace(0f, 0f, 1f)

    /**
     * Adds the quad of an evaluated [PlayableSprite] to the batch.
     *
     * @param sprite The sprite to draw.
     * @param region The texture region of the sprite's current frame.
     * @param alphaMultiplier An additional alpha multiplier (the component's draw alpha).
     * @param colorMultiplier An additional color multiplier, used to dim the sprite.
     */
    @JvmOverloads
    fun draw(sprite: PlayableSprite, region: GraphicsTexture.Region, alphaMultiplier: Float, colorMultiplier: Float = 1f) {
        val alpha = sprite.alpha * alphaMultiplier

        prepare(region.texture, sprite.additiveBlend)

        val width = region.width
        val height = region.height
        val origin = sprite.element.origin

        // Flips and negative vector scales mirror the sprite in place, keeping the geometry
        // anchored at the original origin (matching osu!lazer's origin adjustment). A flip
        // combined with a negative scale on the same axis cancels out.
        val scaleX = abs(sprite.scaleX)
        val scaleY = abs(sprite.scaleY)
        val mirrorX = sprite.flipHorizontal != (sprite.scaleX < 0f)
        val mirrorY = sprite.flipVertical != (sprite.scaleY < 0f)

        var l = -width * origin.factorX * scaleX
        var t = -height * origin.factorY * scaleY
        var r = l + width * scaleX
        var b = t + height * scaleY

        var u1 = region.u0
        var u2 = region.u1
        var v1 = region.v0
        var v2 = region.v1

        if (mirrorX) {
            val tmp = u1; u1 = u2; u2 = tmp
        }

        if (mirrorY) {
            val tmp = v1; v1 = v2; v2 = tmp
        }

        // Straight (non-premultiplied) vertex color; the alpha is applied by the blend function.
        val cr = sprite.red * colorMultiplier
        val cg = sprite.green * colorMultiplier
        val cb = sprite.blue * colorMultiplier

        val x = spaceOffsetX + sprite.x * spaceScale
        val y = spaceOffsetY + sprite.y * spaceScale
        val scale = spaceScale

        var i = offset

        if (sprite.rotation == 0f) {
            l = l * scale + x
            t = t * scale + y
            r = r * scale + x
            b = b * scale + y

            i = putVertex(i, l, t, u1, v1, cr, cg, cb, alpha)
            i = putVertex(i, r, t, u2, v1, cr, cg, cb, alpha)
            i = putVertex(i, l, b, u1, v2, cr, cg, cb, alpha)
            i = putVertex(i, r, b, u2, v2, cr, cg, cb, alpha)
        } else {
            val s = sin(sprite.rotation)
            val c = cos(sprite.rotation)

            i = putVertex(i, (l * c - t * s) * scale + x, (l * s + t * c) * scale + y, u1, v1, cr, cg, cb, alpha)
            i = putVertex(i, (r * c - t * s) * scale + x, (r * s + t * c) * scale + y, u2, v1, cr, cg, cb, alpha)
            i = putVertex(i, (l * c - b * s) * scale + x, (l * s + b * c) * scale + y, u1, v2, cr, cg, cb, alpha)
            i = putVertex(i, (r * c - b * s) * scale + x, (r * s + b * c) * scale + y, u2, v2, cr, cg, cb, alpha)
        }

        offset = i

        if (offset == vertices.size) {
            flush()
        }
    }

    /**
     * Adds an axis-aligned quad to the batch, used for the background and dim overlays. The
     * coordinates are in component space (unaffected by [setSpace]).
     *
     * @param region The texture region to draw.
     * @param left The left of the quad.
     * @param top The top of the quad.
     * @param right The right of the quad.
     * @param bottom The bottom of the quad.
     * @param red The red component.
     * @param green The green component.
     * @param blue The blue component.
     * @param alpha The alpha component.
     */
    fun drawQuad(
        region: GraphicsTexture.Region,
        left: Float, top: Float, right: Float, bottom: Float,
        red: Float, green: Float, blue: Float, alpha: Float
    ) {
        prepare(region.texture, false)

        var i = offset
        i = putVertex(i, left, top, region.u0, region.v0, red, green, blue, alpha)
        i = putVertex(i, right, top, region.u1, region.v0, red, green, blue, alpha)
        i = putVertex(i, left, bottom, region.u0, region.v1, red, green, blue, alpha)
        i = putVertex(i, right, bottom, region.u1, region.v1, red, green, blue, alpha)
        offset = i

        if (offset == vertices.size) {
            flush()
        }
    }

    /**
     * Flushes any pending quads.
     */
    fun end() {
        flush()

        renderer = null
    }

    private fun prepare(texture: GraphicsTexture, additive: Boolean) {
        if (texture !== this.texture || additive != this.additiveBlend) {
            flush()
            this.texture = texture
            this.additiveBlend = additive
        }
    }

    private fun putVertex(
        index: Int,
        x: Float, y: Float, u: Float, v: Float,
        r: Float, g: Float, b: Float, a: Float
    ): Int {
        val ary = vertices
        var i = index
        ary[i++] = x
        ary[i++] = y
        ary[i++] = u
        ary[i++] = v
        ary[i++] = r
        ary[i++] = g
        ary[i++] = b
        ary[i++] = a
        return i
    }

    /**
     * Issues the pending quads to the renderer. Automatically called on texture or blend changes,
     * on capacity, and by [end].
     */
    fun flush() {
        val renderer = renderer ?: return
        val texture = texture

        if (offset == 0 || texture == null) {
            return
        }

        renderer.setState(
            shader = BuiltIn.Shaders.TextureQuad,
            texture = texture,
            blendMode = if (additiveBlend) BlendMode.Additive else BlendMode.Alpha,
            uniformSet = uniforms
        )

        val ary = vertices
        val count = offset
        val p = points

        renderer.drawGeometry { transform, baseColor ->
            val alphaMultiplier = baseColor.alpha
            var i = 0

            while (i < count) {
                // The four vertices of a quad share their color.
                val color = Color4(ary[i + 4], ary[i + 5], ary[i + 6], ary[i + 7] * alphaMultiplier)

                for (corner in 0 until 4) {
                    val base = i + corner * FLOATS_PER_VERTEX
                    transform.transform(ary[base], ary[base + 1], p, corner * 2)
                }

                // The two triangles of the quad, as the index buffer of the AndEngine batch did:
                // (0, 1, 2) and (1, 3, 2).
                with(renderer) {
                    addVertex(p[0], p[1], ary[i + 2], ary[i + 3], color)
                    addVertex(p[2], p[3], ary[i + 10], ary[i + 11], color)
                    addVertex(p[4], p[5], ary[i + 18], ary[i + 19], color)

                    addVertex(p[2], p[3], ary[i + 10], ary[i + 11], color)
                    addVertex(p[6], p[7], ary[i + 26], ary[i + 27], color)
                    addVertex(p[4], p[5], ary[i + 18], ary[i + 19], color)
                }

                i += FLOATS_PER_QUAD
            }
        }

        offset = 0
    }

    companion object {
        private const val MAX_QUADS = 1024
        private const val FLOATS_PER_VERTEX = 8
        private const val FLOATS_PER_QUAD = 4 * FLOATS_PER_VERTEX
    }
}
