package com.reco1l.verktex.ui

import com.reco1l.verktex.data.Dimension
import com.reco1l.verktex.data.dip
import com.reco1l.verktex.graphics.GraphicsRenderer
import com.reco1l.verktex.time.TickingClock
import com.reco1l.verktex.ui.container.UIContainer
import kotlin.math.min

inline fun UIContainer.loadingIndicator(builder: UILoadingIndicator.() -> Unit = {}) = UILoadingIndicator().apply(builder).also { +it }

/**
 * A spinning arc indicating that something is in progress.
 */
open class UILoadingIndicator : UICanvas() {

    /**
     * The thickness of the arc.
     */
    var thickness: Dimension.Fixed = 3f.dip

    /**
     * The rotation speed of the arc in degrees per second.
     */
    var rotationSpeed = 360f


    private var angle = 0f


    init {
        width = 32f.dip
        height = 32f.dip

        style = Style { theme ->
            color = theme.palette.accent
        }
    }


    override fun onUpdate(clock: TickingClock) {
        angle = (angle + rotationSpeed * clock.elapsedTickTime) % 360f
        super.onUpdate(clock)
    }

    override fun onDraw(renderer: GraphicsRenderer) {
        val size = min(bounds.width, bounds.height)

        renderer.drawArcStroke(
            x = (bounds.width - size) / 2f,
            y = (bounds.height - size) / 2f,
            width = size,
            height = size,
            thickness = thickness.toPixels(),
            startAngle = angle,
            endAngle = angle + ARC_LENGTH,
            color = contentColor
        )
    }


    companion object {
        private const val ARC_LENGTH = 270f
    }
}
