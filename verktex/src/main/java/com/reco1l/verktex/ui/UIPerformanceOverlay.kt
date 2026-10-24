package com.reco1l.verktex.ui

import com.reco1l.verktex.BuiltIn
import com.reco1l.verktex.PerformanceStats
import com.reco1l.verktex.data.Anchor
import com.reco1l.verktex.data.Color4
import com.reco1l.verktex.data.Padding
import com.reco1l.verktex.data.dip
import com.reco1l.verktex.graphics.GraphicsRenderer
import com.reco1l.verktex.graphics.GraphicsShader
import com.reco1l.verktex.time.TickingClock
import com.reco1l.verktex.ui.container.UILinearContainer
import com.reco1l.verktex.ui.text.UIText
import kotlin.math.min

/**
 * Shows the frame rate, the frame, draw and update times and a graph of the last frame times in a
 * corner of the window. Enabled with the `ShowPerformanceOverlay` setting.
 */
class UIPerformanceOverlay : UILinearContainer() {

    private val label = UIText().apply {
        fontSize = 12f.dip
    }

    private var timeSinceLabelUpdate = LABEL_INTERVAL


    init {
        orientation = Orientation.Vertical
        anchor = Anchor.TopRight
        origin = Anchor.TopRight
        x = (-8f).dip
        y = 8f.dip
        padding = Padding(6f.dip)
        spacing = 4f.dip
        radius = 6f.dip
        backgroundColor = Color4(0xB0000000)

        +label
        +Graph()
    }


    override fun onUpdate(clock: TickingClock) {
        // Re-measuring the text is not free, so it refreshes a few times per second only.
        timeSinceLabelUpdate += clock.elapsedTickTime

        if (timeSinceLabelUpdate >= LABEL_INTERVAL) {
            timeSinceLabelUpdate = 0f
            label.text = "%.0f fps   draw %.1f ms   update %.2f ms   %d draw calls".format(
                PerformanceStats.fps,
                PerformanceStats.drawTime,
                PerformanceStats.updateTime,
                PerformanceStats.drawCommands
            )
        }

        super.onUpdate(clock)
    }


    /**
     * One bar per frame, green up to 120 fps, yellow up to 60 fps, red below, with guides at both.
     */
    private class Graph : UICanvas() {

        init {
            width = (PerformanceStats.HISTORY_SIZE * BAR_WIDTH).dip
            height = GRAPH_HEIGHT.dip
        }

        override fun onDraw(renderer: GraphicsRenderer) = with(renderer) {
            val width = bounds.width
            val height = bounds.height
            val barWidth = width / PerformanceStats.HISTORY_SIZE

            // All bars share one draw call, as a plain quad shader needs no per-rectangle uniforms.
            setState(shader = BuiltIn.Shaders.SolidQuad, uniformSet = GraphicsShader.UniformSet())

            drawGeometry { transform, baseColor ->
                for (i in 0 until PerformanceStats.HISTORY_SIZE) {
                    val frameTime = PerformanceStats.frameTimeAt(i)
                    if (frameTime <= 0f) continue

                    val barHeight = min(frameTime / MAX_FRAME_TIME, 1f) * height
                    val color = when {
                        frameTime <= FRAME_TIME_120 -> GOOD_COLOR
                        frameTime <= FRAME_TIME_60 -> OK_COLOR
                        else -> BAD_COLOR
                    }.copy(alpha = baseColor.alpha)

                    val x = i * barWidth
                    val y = height - barHeight

                    val (x0, y0) = transform.transform(x, y)
                    val (x1, y1) = transform.transform(x + barWidth, y)
                    val (x2, y2) = transform.transform(x + barWidth, height)
                    val (x3, y3) = transform.transform(x, height)

                    addVertex(x0, y0, 0f, 0f, color)
                    addVertex(x1, y1, 1f, 0f, color)
                    addVertex(x2, y2, 1f, 1f, color)

                    addVertex(x0, y0, 0f, 0f, color)
                    addVertex(x2, y2, 1f, 1f, color)
                    addVertex(x3, y3, 0f, 1f, color)
                }
            }

            for (guide in floatArrayOf(FRAME_TIME_120, FRAME_TIME_60)) {
                drawRect(0f, height - guide / MAX_FRAME_TIME * height, width, 1f, GUIDE_COLOR)
            }
        }
    }


    companion object {
        private const val LABEL_INTERVAL = 0.25f
        private const val BAR_WIDTH = 2f
        private const val GRAPH_HEIGHT = 48f

        // Milliseconds, the graph is full at 30 fps.
        private const val MAX_FRAME_TIME = 1000f / 30f
        private const val FRAME_TIME_120 = 1000f / 120f
        private const val FRAME_TIME_60 = 1000f / 60f

        private val GOOD_COLOR = Color4(0xFFA6E3A1)
        private val OK_COLOR = Color4(0xFFF9E2AF)
        private val BAD_COLOR = Color4(0xFFF38BA8)
        private val GUIDE_COLOR = Color4(0x60FFFFFF)
    }
}
