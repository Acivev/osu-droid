package com.rian.andengine.modifier

import com.osudroid.verktex.VerktexTestFixtures
import com.reco1l.verktex.ui.container.UIContainer
import com.reco1l.verktex.ui.shape.UIBox
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

class UniversalModifierTest {

    @Before
    fun setUp() {
        VerktexTestFixtures.ensureInitialized()
    }

    @Test
    fun fadeToReachesTheTargetAtDuration() {
        val box = UIBox()

        box.fadeTo(0f, 1f)

        ModifierRegistry.tick(0.5f)
        assertEquals(0.5f, box.alpha, 1e-4f)

        ModifierRegistry.tick(0.5f)
        assertEquals(0f, box.alpha, 1e-4f)
        assertEquals(0, ModifierRegistry.activeCount())
    }

    @Test
    fun absoluteSequenceUsesTheGivenStart() {
        val box = UIBox()

        box.beginAbsoluteSequence(ModifierRegistry.time + 2f) { fadeTo(0f, 1f) }

        ModifierRegistry.tick(2f)
        assertEquals(1f, box.alpha, 1e-4f)

        ModifierRegistry.tick(0.5f)
        assertEquals(0.5f, box.alpha, 1e-4f)

        ModifierRegistry.tick(0.5f)
        assertEquals(0f, box.alpha, 1e-4f)
    }

    @Test
    fun clearModifiersAfterDropsLaterOnes() {
        val box = UIBox()

        box.beginModifierSequence {
            fadeTo(0f, 1f)
            then()
            scaleTo(2f, 1f)
        }

        box.clearModifiersAfter(ModifierRegistry.time + 1f)

        ModifierRegistry.tick(3f)
        assertEquals(0f, box.alpha, 1e-4f)
        assertEquals(1f, box.scaleX, 1e-4f)
        assertEquals(1f, box.scaleY, 1e-4f)
    }

    @Test
    fun finishCallbackRunsOnce() {
        val box = UIBox()
        var count = 0

        box.fadeOut(1f).after { count++ }

        ModifierRegistry.tick(1f)
        ModifierRegistry.tick(1f)
        assertEquals(1, count)
    }

    @Test
    fun propagateChildrenClearsDescendants() {
        val parent = UIContainer()
        val child = UIBox()
        parent += child

        child.fadeTo(0f, 1f)
        parent.clearModifiers(ModifierType.Alpha, true)

        ModifierRegistry.tick(1f)
        assertEquals(1f, child.alpha, 1e-4f)
    }
}
