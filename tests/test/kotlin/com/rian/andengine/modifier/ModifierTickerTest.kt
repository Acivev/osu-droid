package com.rian.andengine.modifier

import com.osudroid.verktex.VerktexTestFixtures
import com.reco1l.verktex.ui.container.UIContainer
import com.reco1l.verktex.ui.shape.UIBox
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

class ModifierTickerTest {

    @Before
    fun setUp() {
        VerktexTestFixtures.ensureInitialized()
    }

    @Test
    fun tickerSetTimeDrivesAbsoluteSequences() {
        val root = UIContainer()
        val child = UIBox()
        root += child

        val ticker = ModifierTicker()
        ModifierRegistry.bindTicker(root, ticker)

        try {
            child.beginAbsoluteSequence(10f) { fadeTo(0f, 1f) }

            ticker.time = 10.5f
            assertEquals(0.5f, child.alpha, 1e-4f)

            // Backwards within the running modifier.
            ticker.time = 10.25f
            assertEquals(0.75f, child.alpha, 1e-4f)

            ticker.time = 11f
            assertEquals(0f, child.alpha, 1e-4f)
        } finally {
            ModifierRegistry.release(root)
            ModifierRegistry.bindTicker(root, null)
        }
    }

    @Test
    fun wallTickDoesNotMoveABoundSubtree() {
        val root = UIContainer()
        val child = UIBox()
        root += child

        val ticker = ModifierTicker()
        ModifierRegistry.bindTicker(root, ticker)

        try {
            child.fadeTo(0f, 1f)

            ModifierRegistry.tick(5f)
            assertEquals(1f, child.alpha, 1e-4f)
        } finally {
            ModifierRegistry.release(root)
            ModifierRegistry.bindTicker(root, null)
        }
    }
}
