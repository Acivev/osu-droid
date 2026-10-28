package com.osudroid.game.cursor.trail

import com.reco1l.verktex.BuiltIn
import com.reco1l.verktex.data.Color4
import com.reco1l.verktex.data.px
import com.reco1l.verktex.graphics.BlendMode
import com.reco1l.verktex.graphics.GraphicsRenderer
import com.reco1l.verktex.graphics.GraphicsShader
import com.reco1l.verktex.ui.UICanvas
import ru.nsu.ccfit.zuev.osu.Config
import kotlin.math.cos
import kotlin.math.hypot

/**
 * A ribbon-style cursor trail that renders a rainbow-colored triangle-strip path.
 *
 * Each trail control-point is added when the cursor moves ≥ 5 units, then fades
 * out over ~0.5 s. The ribbon is drawn with additive blending and per-vertex
 * rainbow colors.
 */
class FancyCursorTrail : UICanvas() {

    /** Half-width of the ribbon at its newest (widest) point, in screen pixels. */
    var trailWidth = 8f

    /** Current cursor position; update this every frame before calling [update]. */
    var cursorX = 0f
    var cursorY = 0f

    // Trail state

    private class TrailPiece(
        val x: Float,
        val y: Float,
        val startWidth: Float
    ) {
        var alpha = 1f
        var r = 1f
        var g = 1f
        var b = 1f
        var width = startWidth
    }

    private val pieces = ArrayList<TrailPiece>(256)
    private var lastX = Float.NaN
    private var lastY = Float.NaN
    private var totalTime = 0f

    // Client-side vertex buffer (interleaved: x, y, r, g, b, a)

    private var stagingBuffer: FloatArray? = null

    // Scene-space position of one vertex.
    private val point = FloatArray(2)

    private val uniforms = GraphicsShader.UniformSet()

    // Reusable float array to avoid per-call allocation in rainbowColor()
    private val tempRgb = FloatArray(3)

    init {
        // GameScene drives updates manually; a Verktex canvas has no update of its own.
        // Additive blending gives a nice neon glow effect
        blendMode = BlendMode.Additive

        // The ribbon is drawn anywhere in the playfield, Verktex skips a component outside the window by its size.
        width = Config.getRES_WIDTH().toFloat().px
        height = Config.getRES_HEIGHT().toFloat().px
    }


    /**
     * Advance the trail simulation by [dt] seconds and collect a new control-point
     * if the cursor has moved far enough.  Call this every game-loop frame.
     */
    fun update(dt: Float) {
        totalTime += dt

        // Age existing pieces (reverse iteration so removal is safe)
        var i = pieces.size - 1
        while (i >= 0) {
            val p = pieces[i]
            val hue = p.alpha * 10f + totalTime * 25f
            rainbowColor(hue, tempRgb)
            p.r = tempRgb[0]
            p.g = tempRgb[1]
            p.b = tempRgb[2]

            p.alpha -= dt * FADE_OUT_SPEED
            if (p.alpha <= 0f) {
                pieces.removeAt(i)
            } else {
                p.width = p.startWidth * p.alpha
            }
            i--
        }

        // Emit a new control-point when the cursor travels ≥ MIN_DIST
        if (lastX.isNaN()) {
            lastX = cursorX
            lastY = cursorY
        }
        val dx = cursorX - lastX
        val dy = cursorY - lastY
        if (hypot(dx, dy) >= MIN_DIST) {
            lastX = cursorX
            lastY = cursorY
            pieces.add(TrailPiece(cursorX, cursorY, trailWidth))
        }
    }

    /** Clear all trail state (e.g. when gameplay ends / cursor is hidden). */
    fun resetTrail() {
        pieces.clear()
        lastX = Float.NaN
        lastY = Float.NaN
        totalTime = 0f
    }

    // Entity rendering

    override fun onDraw(renderer: GraphicsRenderer) {
        val n = pieces.size
        if (n < 2) return

        // Total vertices in the triangle-strip: 2 per segment between control-points
        val vertCount = (n - 1) * 2
        val floatsPerVert = 6          // x, y, r, g, b, a
        val totalFloats = vertCount * floatsPerVert

        // Grow the staging buffer when needed
        var buf = stagingBuffer
        if (buf == null || buf.size < totalFloats) {
            buf = FloatArray(totalFloats + 12)
            stagingBuffer = buf
        }

        // Fill vertex data
        var position = 0
        fun FloatArray.put(value: Float) {
            this[position++] = value
        }

        for (idx in 1 until n) {
            val prev = pieces[idx - 1]
            val curr = pieces[idx]

            // Perpendicular to the segment direction
            val diffX = curr.x - prev.x
            val diffY = curr.y - prev.y
            var perpX = diffY
            var perpY = -diffX
            val len = hypot(perpX, perpY)
            if (len > 0f) {
                perpX /= len
                perpY /= len
            }

            // Vertex A – on the 'left' of prev
            buf.put(prev.x - perpX * prev.width)
            buf.put(prev.y - perpY * prev.width)
            buf.put(prev.r)
            buf.put(prev.g)
            buf.put(prev.b)
            buf.put(prev.alpha)

            // Vertex B – on the 'right' of curr
            buf.put(curr.x + perpX * curr.width)
            buf.put(curr.y + perpY * curr.width)
            buf.put(curr.r)
            buf.put(curr.g)
            buf.put(curr.b)
            buf.put(curr.alpha)
        }

        // The solid shader with every vertex at the centre of the UV space, the colours per vertex.
        renderer.setState(shader = BuiltIn.Shaders.SolidQuad, uniformSet = uniforms)

        // The triangle strip as separate triangles: vertices i, i + 1 and i + 2 form one.
        renderer.drawGeometry { transform, _ ->
            for (i in 0 until vertCount - 2) {
                for (j in i..i + 2) {
                    val base = j * floatsPerVert
                    transform.transform(buf[base], buf[base + 1], point, 0)

                    val color = renderer.transformColor(Color4(buf[base + 2], buf[base + 3], buf[base + 4], buf[base + 5]))
                    with(renderer) { addVertex(point[0], point[1], 0.5f, 0.5f, color) }
                }
            }
        }
    }

    // Helpers

    /**
     * Cosine-based rainbow RGB. Result channels are in [0, 1].
     */
    private fun rainbowColor(t: Float, out: FloatArray) {
        val td = t.toDouble()
        val r = ((cos(td)           + 1.0) / 2.0).coerceIn(0.0, 1.0).toFloat()
        val g = ((cos(td - 2.09440) + 1.0) / 2.0).coerceIn(0.0, 1.0).toFloat()  // 2π/3
        val b = ((cos(td - 4.18879) + 1.0) / 2.0).coerceIn(0.0, 1.0).toFloat()  // 4π/3

        // Mix with white at saturation 0.5 and boost brightness
        val sat = 0.5f
        val bri = 1.5f
        out[0] = (r * sat * bri + (1f - sat)).coerceIn(0f, 1f)
        out[1] = (g * sat * bri + (1f - sat)).coerceIn(0f, 1f)
        out[2] = (b * sat * bri + (1f - sat)).coerceIn(0f, 1f)
    }

    companion object {
        private const val FADE_OUT_SPEED = 2f
        private const val MIN_DIST = 5f
    }
}

