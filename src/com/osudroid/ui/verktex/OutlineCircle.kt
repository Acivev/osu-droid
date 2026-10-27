package com.osudroid.ui.verktex

import com.reco1l.verktex.graphics.GraphicsRenderer
import com.reco1l.verktex.ui.UICanvas

/**
 * The AndEngine UICircle with the outline paint style: a line [lineWidth] window pixels wide
 * along the edge of the circle, half of it inside and half outside, as glLineWidth drew it.
 */
open class OutlineCircle : UICanvas() {

    /**
     * The width of the line in window pixels.
     */
    var lineWidth = 1f

    override fun onDraw(renderer: GraphicsRenderer) {
        val half = lineWidth / 2f

        renderer.drawArcStroke(
            x = -half,
            y = -half,
            width = bounds.width + lineWidth,
            height = bounds.height + lineWidth,
            thickness = lineWidth,
            color = contentColor
        )
    }
}
