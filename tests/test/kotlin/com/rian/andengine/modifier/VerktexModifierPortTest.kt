package com.rian.andengine.modifier

import com.osudroid.verktex.VerktexTestFixtures
import com.reco1l.verktex.ui.container.UIContainer
import com.reco1l.verktex.ui.shape.UIBox
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * The reco1l component members the playfield port uses on Verktex components: `isAnimating`,
 * `removeModifier` and `beginDelayedSequence`.
 */
class VerktexModifierPortTest {

    @Before
    fun setUp() {
        VerktexTestFixtures.ensureInitialized()
    }

    private inline fun withTicker(block: (child: UIBox, ticker: ModifierTicker) -> Unit) {
        val root = UIContainer()
        val child = UIBox()
        root += child

        val ticker = ModifierTicker()
        ModifierRegistry.bindTicker(root, ticker)

        try {
            block(child, ticker)
        } finally {
            ModifierRegistry.release(root)
            ModifierRegistry.bindTicker(root, null)
        }
    }

    @Test
    fun isAnimatingWhileAModifierIsActive() = withTicker { child, ticker ->
        assertFalse(child.isAnimating)

        child.fadeTo(0f, 1f)
        assertTrue(child.isAnimating)

        ticker.time = 2f
        assertFalse(child.isAnimating)
    }

    @Test
    fun removedModifierIsNotApplied() = withTicker { child, ticker ->
        val modifier = child.fadeTo(0f, 1f)

        assertTrue(child.removeModifier(modifier))
        assertFalse(child.removeModifier(modifier))

        ticker.time = 0.5f
        assertEquals(1f, child.alpha, 1e-4f)
    }

    @Test
    fun delayedSequenceStartsAfterTheDelay() = withTicker { child, ticker ->
        child.beginDelayedSequence(1f) { it.fadeTo(0f, 1f) }

        ticker.time = 0.5f
        assertEquals(1f, child.alpha, 1e-4f)

        ticker.time = 1.5f
        assertEquals(0.5f, child.alpha, 1e-4f)

        // The delay does not stay on the component.
        assertEquals(0f, child.modifierDelay, 0f)
    }
}
