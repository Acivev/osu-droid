package com.reco1l.osu.ui.entity

import com.osudroid.ui.verktex.LegacyUnits
import com.reco1l.verktex.data.Dimension
import com.reco1l.verktex.data.fill
import com.reco1l.verktex.data.px
import com.reco1l.verktex.input.InputEvent
import com.reco1l.verktex.input.PointerEvent
import com.reco1l.verktex.time.TickingClock
import com.reco1l.verktex.ui.UIComponent
import com.reco1l.verktex.ui.container.UIContainer
import ru.nsu.ccfit.zuev.osu.Config
import ru.nsu.ccfit.zuev.osu.Utils
import kotlin.math.abs
import kotlin.math.sign

@Deprecated("Use ScrollableContainer instead.")
abstract class ScrollableList : UIContainer()
{

    var isScroll = false
        private set

    // Scroll detection state, in window pixels.
    private var scrollDetectorPointerId = -1
    private var scrollDetectorTriggering = false
    private var scrollDetectorLastX = 0f
    private var scrollDetectorLastY = 0f

    // The child that consumed a press gets every event of its pointer until it is lifted.
    private val inputBindings = arrayOfNulls<UIComponent>(10)

    private var percentShow = -1f
    private var maxY = 100500f
    private var pointerId = -1
    private var tapTime = 0f
    private var scrollStartTimeMs = 0L   // wall-clock time of the last onScrollStarted call
    private var cumulativeScrollY = 0f

    protected var velocityY = 0f
    protected var camY = -146f
    protected var itemHeight = 0f

    init {
        width = fill()
        height = fill()
    }

    override fun onInputEvent(event: InputEvent): Boolean
    {
        if (event !is PointerEvent)
            return false

        val touchX = event.x
        val touchY = event.y

        when (event.action)
        {
            PointerEvent.Action.Down -> prepareScroll(event.pointerId, touchX, touchY)

            PointerEvent.Action.Move ->
                if (scrollDetectorPointerId == -1)
                    prepareScroll(event.pointerId, touchX, touchY)
                else if (scrollDetectorPointerId == event.pointerId)
                {
                    val distanceX = touchX - scrollDetectorLastX
                    val distanceY = touchY - scrollDetectorLastY

                    if (scrollDetectorTriggering || abs(distanceX) > TRIGGER_SCROLL_MINIMUM_DISTANCE || abs(distanceY) > TRIGGER_SCROLL_MINIMUM_DISTANCE)
                    {
                        if (!scrollDetectorTriggering)
                            onScrollStarted(scrollDetectorPointerId, distanceX, distanceY)
                        else
                            onScroll(scrollDetectorPointerId, distanceX, distanceY)

                        scrollDetectorLastX = touchX
                        scrollDetectorLastY = touchY
                        scrollDetectorTriggering = true
                    }
                }

            PointerEvent.Action.Up, PointerEvent.Action.Unknown ->
                if (scrollDetectorPointerId == event.pointerId)
                {
                    if (scrollDetectorTriggering)
                    {
                        scrollDetectorTriggering = false
                        onScrollFinished(scrollDetectorPointerId, touchX - scrollDetectorLastX, touchY - scrollDetectorLastY)
                    }

                    scrollDetectorPointerId = -1
                }
        }

        return dispatchToChildren(event)
    }

    private fun prepareScroll(pointerID: Int, touchX: Float, touchY: Float)
    {
        scrollDetectorLastX = touchX
        scrollDetectorLastY = touchY
        scrollDetectorTriggering = false
        scrollDetectorPointerId = pointerID
    }

    private fun dispatchToChildren(event: PointerEvent): Boolean
    {
        val inputBinding = inputBindings.getOrNull(event.pointerId)

        if (inputBinding != null && inputBinding.parent == this && event.action != PointerEvent.Action.Down)
        {
            inputBinding.onInputEvent(event)

            if (event.action == PointerEvent.Action.Up || event.action == PointerEvent.Action.Unknown)
                inputBindings[event.pointerId] = null

            return true
        }
        else if (event.pointerId in inputBindings.indices)
            inputBindings[event.pointerId] = null

        for (child in children.asReversed())
        {
            if (child.containsScenePoint(event.x, event.y) && child.onInputEvent(event))
            {
                if (event.pointerId in inputBindings.indices)
                    inputBindings[event.pointerId] = child

                return true
            }
        }

        return false
    }

    /**
     * Whether a child consumed a press of [pointerId] that is not lifted yet.
     */
    protected fun isBound(pointerId: Int) = inputBindings.getOrNull(pointerId)?.parent == this

    override fun onUpdate(clock: TickingClock)
    {
        super.onUpdate(clock)

        val secondsElapsed = clock.elapsedTickTime

        if (children.isEmpty())
            return

        if (percentShow == -1f)
        {
            var y = -camY

            for (sprite in children)
            {
                val height = (sprite.height as? Dimension.Fixed ?: continue).toPixels() / LegacyUnits.scale

                sprite.y = (y * LegacyUnits.scale).px
                y += height + 5f
            }

            y += camY
            camY += velocityY * secondsElapsed
            maxY = y - 0.8f * (Config.getRES_HEIGHT() - 110 - (itemHeight - 32))

            if (camY <= -146 && velocityY < 0 || camY > maxY && velocityY > 0)
            {
                camY -= velocityY * secondsElapsed
                velocityY = 0f
                isScroll = false
            }

            if (abs(velocityY) <= Utils.toRes(500) * secondsElapsed)
            {
                velocityY = 0f
                isScroll = false
            }
            else velocityY -= Utils.toRes(10) * secondsElapsed * sign(velocityY)
        }
        else
        {
            percentShow += secondsElapsed * 4

            if (percentShow > 1)
                percentShow = 1f

            for ((i, sprite) in children.withIndex())
            {
                val height = (sprite.height as? Dimension.Fixed ?: continue).toPixels() / LegacyUnits.scale

                sprite.x = (-160f * LegacyUnits.scale).px
                sprite.y = ((146 + 0.8f * percentShow * i * (height + 5f)) * LegacyUnits.scale).px
            }

            if (percentShow == 1f)
                percentShow = -1f
        }
    }

    private fun onScrollStarted(pointerID: Int, distanceX: Float, distanceY: Float)
    {
        velocityY = 0f
        pointerId = pointerID
        tapTime = 0f
        scrollStartTimeMs = System.currentTimeMillis()
        cumulativeScrollY = 0f
        isScroll = true
    }

    private fun onScroll(pointerID: Int, distanceX: Float, distanceY: Float)
    {
        if (pointerId == -1 || pointerId == pointerID)
        {
            isScroll = true
            cumulativeScrollY += distanceY
            camY -= distanceY

            if (camY <= -146)
            {
                camY = -146f
                velocityY = 0f
            }
            else if (camY >= maxY)
            {
                camY = maxY
                velocityY = 0f
            }
        }
    }

    private fun onScrollFinished(pointerID: Int, distanceX: Float, distanceY: Float)
    {
        if (pointerId == -1 || pointerId == pointerID)
        {
            // Use wall-clock elapsed time so velocity is independent of the game tick rate.
            val elapsedSec = (System.currentTimeMillis() - scrollStartTimeMs) / 1000f
            velocityY = if (elapsedSec < 0.001f) 0f else cumulativeScrollY / elapsedSec
            isScroll = velocityY != 0f
            pointerId = -1
        }
    }

    private companion object
    {
        // How far a pointer must move before a scroll starts.
        const val TRIGGER_SCROLL_MINIMUM_DISTANCE = 10f
    }
}
