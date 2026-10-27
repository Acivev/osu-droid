package com.osudroid.ui.v2.modmenu

import com.reco1l.verktex.data.LayoutConstraints
import com.reco1l.verktex.data.Vec2
import com.reco1l.verktex.data.Vec4
import com.reco1l.verktex.input.InputEvent
import com.reco1l.verktex.input.PointerEvent
import com.reco1l.verktex.time.TickingClock
import com.reco1l.verktex.ui.UIComponent
import com.reco1l.verktex.ui.container.UILinearContainer
import kotlin.math.max

/**
 * A [UILinearContainer] leaving its hidden children out of the layout and the presses, as the
 * AndEngine linear container did. Verktex keeps hidden components in the layout.
 * Unlike [UILinearContainer] it has no fill share pass on the main axis, its children have a
 * size of their own along it.
 */
internal class VisibleChildrenContainer : UILinearContainer() {

    // The children that were visible when the layout was last invalidated, in order.
    private val visibleChildren = mutableListOf<UIComponent>()

    override fun onUpdate(clock: TickingClock) {
        // Visibility changes do not invalidate the layout by themselves.
        var index = 0
        var changed = false

        for (child in children) {
            if (!child.isVisible) continue

            if (index >= visibleChildren.size || visibleChildren[index] !== child) {
                changed = true
                break
            }
            index++
        }

        if (changed || index != visibleChildren.size) {
            visibleChildren.clear()
            children.filterTo(visibleChildren) { it.isVisible }
            invalidateMeasure()
        }

        super.onUpdate(clock)
    }

    override fun onMeasure(parentConstraints: LayoutConstraints, contentConstraints: LayoutConstraints): Vec2 {
        val isVertical = orientation == Orientation.Vertical

        var mainSize = 0f
        var crossSize = 0f
        var visibleCount = 0

        for (child in children) {
            if (!child.isVisible) continue

            val childSize = child.measure(contentConstraints.loosen().withoutFillLimits(child))

            mainSize += if (isVertical) childSize.y else childSize.x
            crossSize = max(crossSize, if (isVertical) childSize.x else childSize.y)
            visibleCount++
        }

        if (visibleCount > 1) {
            mainSize += spacing.toPixels() * (visibleCount - 1)
        }

        val contentSize = if (isVertical) Vec2(crossSize, mainSize) else Vec2(mainSize, crossSize)
        val measuredSize = parentConstraints.constrain(contentSize.expand(padding.toVec4()))

        // The main axis keeps the size the child got in the first pass.
        stretchFillingChildren(measuredSize) { child ->
            if (isVertical) {
                contentConstraints.loosen().copy(minHeight = child.measuredSize.y, maxHeight = child.measuredSize.y)
            } else {
                contentConstraints.loosen().copy(minWidth = child.measuredSize.x, maxWidth = child.measuredSize.x)
            }
        }

        return measuredSize
    }

    override fun onLayout(bounds: Vec4) {
        var x = paddingLeft.toPixels()
        var y = paddingTop.toPixels()

        for (child in children) {
            if (!child.isVisible) continue

            child.layout(Vec4(x, y, x + child.measuredSize.x, y + child.measuredSize.y))

            if (orientation == Orientation.Vertical) {
                y += child.measuredSize.y + spacing.toPixels()
            } else {
                x += child.measuredSize.x + spacing.toPixels()
            }
        }
    }

    override fun onInputEvent(event: InputEvent): Boolean {
        // Same as UIContainer, but hidden children do not take new presses.
        for (child in children.asReversed()) {
            if (event is PointerEvent
                && event.action == PointerEvent.Action.Down
                && (!child.isVisible || !child.containsScenePoint(event.x, event.y))) {
                continue
            }

            if (child.onInputEvent(event)) {
                return true
            }
        }
        return false
    }
}
