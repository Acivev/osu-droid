package com.osudroid.ui.verktex

import com.reco1l.verktex.data.Anchor
import com.reco1l.verktex.data.fill
import com.reco1l.verktex.time.TickingClock
import com.reco1l.verktex.ui.container.UIContainer
import com.reco1l.verktex.ui.shape.UICircle

/**
 * The AndEngine CircularProgressBar as it is used, indeterminate: a track circle outlined 4
 * pixels wide and a 0.85-size 36° arc turning 360° per second, both the accent at alpha 0.3.
 * [UILoadingIndicator][com.reco1l.verktex.ui.UILoadingIndicator] draws a 270° stroke instead.
 */
open class CircularProgressBar : UIContainer() {

    private val trackCircle = OutlineCircle().apply {
        width = fill()
        height = fill()
        lineWidth = 4f
        style = Style { theme ->
            color = theme.palette.accent
            alpha = 0.3f
        }
    }

    private val rotatingCircle = UICircle().apply {
        width = fill(0.85f)
        height = fill(0.85f)
        anchor = Anchor.Center
        origin = Anchor.Center
        rotationOrigin = Anchor.Center
        // The indeterminate portion 0.25 is only applied on a progress change, which never happens.
        setPortion(0.1f)
        style = Style { theme ->
            color = theme.palette.accent
            alpha = 0.3f
        }
    }

    init {
        +trackCircle
        +rotatingCircle
    }

    override fun onUpdate(clock: TickingClock) {
        rotatingCircle.rotationZ += 360f * clock.elapsedTickTime
        super.onUpdate(clock)
    }
}
