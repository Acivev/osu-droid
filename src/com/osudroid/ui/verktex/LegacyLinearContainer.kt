package com.osudroid.ui.verktex

import com.reco1l.verktex.time.TickingClock
import com.reco1l.verktex.ui.UIComponent
import com.reco1l.verktex.ui.container.UIContainer
import com.reco1l.verktex.ui.container.UILinearContainer

inline fun UIContainer.legacyLinearContainer(builder: LegacyLinearContainer.() -> Unit) = LegacyLinearContainer().apply(builder).also { +it }

/**
 * The AndEngine UILinearContainer: invisible children take no space. Verktex keeps them in the
 * layout, so the measure and layout passes, which iterate the container, skip them. They are still
 * updated, drawn (as nothing) and offered input like in any container.
 */
open class LegacyLinearContainer : UILinearContainer() {

    // The visibility of the children the container was last measured with.
    private var measuredVisibility = 0L

    /**
     * Iterates the **visible** children only, for the measure and layout passes of [UILinearContainer].
     *
     * Warning: this changes every `for (child in this)`, `forEach` and `toList` on this container, and the
     * iterator's `remove()` throws. Only measure and layout should rely on it; anything that needs every
     * child (including hidden ones) must use [children], which is the full list.
     */
    override fun iterator(): MutableIterator<UIComponent> = object : MutableIterator<UIComponent> {
        private var index = nextVisible(0)

        private fun nextVisible(from: Int): Int {
            var i = from
            while (i < children.size && !children[i].isVisible) i++
            return i
        }

        override fun hasNext() = index < children.size

        override fun next(): UIComponent {
            val child = children[index]
            index = nextVisible(index + 1)
            return child
        }

        override fun remove() = throw UnsupportedOperationException()
    }

    override fun onUpdate(clock: TickingClock) {
        // Showing or hiding a child does not invalidate the measure in Verktex.
        var visibility = 0L
        for (i in children.indices) {
            if (children[i].isVisible) visibility = visibility or (1L shl (i % 64))
        }

        if (visibility != measuredVisibility) {
            measuredVisibility = visibility
            invalidateMeasure()
        }

        super.onUpdate(clock)
    }
}
