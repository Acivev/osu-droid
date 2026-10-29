package com.osudroid.ui.v2.hud.elements

import com.osudroid.ui.verktex.LegacyUnits.vu
import com.reco1l.verktex.data.Anchor
import com.reco1l.verktex.data.Color4
import com.reco1l.verktex.data.px
import com.reco1l.verktex.graphics.CompareFunction
import com.reco1l.verktex.graphics.DepthState
import com.reco1l.verktex.graphics.GraphicsRenderer
import com.reco1l.verktex.time.TickingClock
import com.reco1l.verktex.ui.shape.UIBox
import com.osudroid.utils.*
import com.osudroid.beatmaps.constants.HitObjectType
import kotlin.math.abs

class HUDBarHitErrorMeter : HUDHitErrorMeter() {

    private val expiredIndicators = SynchronizedPool<Indicator>(20)
    private val activeIndicators = mutableListOf<Indicator>()

    // Indicators are drawn as rectangles of this size, centered like the shared AndEngine box was.
    private val indicatorWidth = INDICATOR_WIDTH.vu
    private val indicatorHeight = (INDICATOR_HEIGHT - 2f).vu


    init {
        width = WIDTH.vu.px
        height = INDICATOR_HEIGHT.vu.px

        val mehWindow = DepthBox().apply {
            anchor = Anchor.Center
            origin = Anchor.Center
            color = mehColor
            radius = (BAR_HEIGHT / 2).vu.px
            width = WIDTH.vu.px
            height = BAR_HEIGHT.vu.px

            depthState = DEPTH_TEST_LESS
        }

        val okWindow = DepthBox().apply {
            anchor = Anchor.Center
            origin = Anchor.Center
            color = okColor
            width = (WIDTH * (hitWindow.okWindow / hitWindow.mehWindow).toFloat()).vu.px
            height = BAR_HEIGHT.vu.px

            depthState = DEPTH_TEST_LESS
        }

        val greatWindow = DepthBox().apply {
            anchor = Anchor.Center
            origin = Anchor.Center
            width = (WIDTH * (hitWindow.greatWindow / hitWindow.mehWindow).toFloat()).vu.px
            height = BAR_HEIGHT.vu.px
            color = greatColor

            clearDepthBeforeDraw = true
            depthState = DEPTH_TEST_LESS
        }

        this += mehWindow
        addToBack(okWindow)
        addToBack(greatWindow)

        // Indicator
        this += DepthBox().apply {
            anchor = Anchor.Center
            origin = Anchor.Center
            width = (INDICATOR_WIDTH / 2).vu.px
            height = INDICATOR_HEIGHT.vu.px
        }

        mehWindow.alpha = 0.6f
        okWindow.alpha = 0.6f
        greatWindow.alpha = 0.6f
    }

    override fun addResult(type: HitObjectType, accuracy: Float, color: Color4) {
        if (type == HitObjectType.Spinner) {
            return
        }

        val accuracyMs = accuracy * 1000

        if (abs(accuracyMs) > hitWindow.mehWindow) {
            return
        }

        val indicator = expiredIndicators.acquire() ?: Indicator(0f, 0f, Color4.White)

        indicator.x = (WIDTH / 2f) * (accuracyMs / hitWindow.mehWindow.toFloat())
        indicator.alpha = 1f
        indicator.color = color

        activeIndicators.add(indicator)
    }

    override fun onSeek() {
        // Note that this only clears current active indicators. While reconstructing indicators that should still be
        // active is possible, it is not worth the complexity.
        activeIndicators.forEach {
            if (!it.isRecycled) {
                expiredIndicators.release(it)
            }
        }
        activeIndicators.clear()
    }

    //region Indicator update & draw

    override fun onDraw(renderer: GraphicsRenderer) {
        super.onDraw(renderer)

        if (activeIndicators.isEmpty()) return

        // Drawn after the children in this element's local space, centered like the shared box was.
        activeIndicators.forEach {
            renderer.drawRect(
                bounds.width / 2f + it.x.vu - indicatorWidth / 2f,
                (bounds.height - indicatorHeight) / 2f,
                indicatorWidth,
                indicatorHeight,
                color = it.color.copy(alpha = it.alpha)
            )
        }
    }

    override fun onUpdate(clock: TickingClock) {
        activeIndicators.forEach(Indicator::update)
        super.onUpdate(clock)
    }

    //endregion


    private inner class Indicator(var x: Float, var alpha: Float, var color: Color4) : IPoolable {
        override var isRecycled = false

        fun update() {
            if (alpha > 0f) {
                alpha -= 0.005f
            }

            if (alpha <= 0f) {
                alpha = 0f
                expiredIndicators.release(this)
                updateThreadUnderTreeLock {
                    activeIndicators.remove(this)
                }
            }
        }
    }

    /**
     * A box that fills itself in its own draw, inside the canvas depth state. [UIBox] only fills its
     * background, which is drawn before the depth state applies.
     */
    private class DepthBox : UIBox() {
        override fun onDraw(renderer: GraphicsRenderer) {
            renderer.drawRect(0f, 0f, bounds.width, bounds.height, color = contentColor, radius = radius.toPixels())
        }
    }


    companion object {

        private const val WIDTH = 400f
        private const val BAR_HEIGHT = 10f
        private const val INDICATOR_HEIGHT = BAR_HEIGHT + 14f
        private const val INDICATOR_WIDTH = 4f

        // The AndEngine DepthInfo.Default and DepthInfo.Less: depth test and write with GL_LESS.
        private val DEPTH_TEST_LESS = DepthState(enabled = true, writeEnabled = true, function = CompareFunction.Less)

    }
}