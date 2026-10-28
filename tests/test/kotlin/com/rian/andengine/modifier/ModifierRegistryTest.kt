package com.rian.andengine.modifier

import com.osudroid.verktex.VerktexTestFixtures
import com.reco1l.verktex.data.pct
import com.reco1l.verktex.data.px
import com.reco1l.verktex.ui.container.UIContainer
import com.reco1l.verktex.ui.shape.UIBox
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Test

class ModifierRegistryTest {

    @Before
    fun setUp() {
        VerktexTestFixtures.ensureInitialized()
    }

    @Test
    fun sequenceStartedBeforeAttachTicks() {
        val root = UIContainer()
        val hud = UIContainer()
        hud.scale = 0.9f

        hud.scaleTo(1f, 0.2f)
        root += hud

        ModifierRegistry.tick(0.2f)
        assertEquals(1f, hud.scaleX, 1e-4f)
        assertEquals(1f, hud.scaleY, 1e-4f)
    }

    @Test
    fun pausedSubtreeDoesNotTick() {
        val root = UIContainer()
        val hud = UIContainer()
        val box = UIBox()
        root += hud
        hud += box

        box.fadeTo(0f, 1f)
        ModifierRegistry.setPaused(hud, true)

        try {
            ModifierRegistry.tick(1f)
            assertEquals(1f, box.alpha, 1e-4f)
        } finally {
            ModifierRegistry.setPaused(hud, false)
        }

        ModifierRegistry.tick(1f)
        assertEquals(0f, box.alpha, 1e-4f)
    }

    @Test
    fun siblingOverlayKeepsTicking() {
        val root = UIContainer()
        val hud = UIContainer()
        val box = UIBox()
        val menu = UIBox()
        root += hud
        hud += box
        root += menu

        box.fadeTo(0f, 1f)
        menu.fadeTo(0f, 1f)
        ModifierRegistry.setPaused(hud, true)

        try {
            ModifierRegistry.tick(1f)
            assertEquals(1f, box.alpha, 1e-4f)
            assertEquals(0f, menu.alpha, 1e-4f)
        } finally {
            ModifierRegistry.setPaused(hud, false)
        }

        ModifierRegistry.tick(1f)
        assertEquals(0f, box.alpha, 1e-4f)
    }

    @Test
    fun pausedSubtreeResumesWhereItStopped() {
        val root = UIContainer()
        val hud = UIContainer()
        val box = UIBox()
        root += hud
        hud += box

        box.fadeTo(0f, 1f)

        ModifierRegistry.tick(0.5f)
        assertEquals(0.5f, box.alpha, 1e-4f)

        ModifierRegistry.setPaused(hud, true)
        ModifierRegistry.tick(1f)
        assertEquals(0.5f, box.alpha, 1e-4f)

        ModifierRegistry.setPaused(hud, false)
        ModifierRegistry.tick(0.25f)
        assertEquals(0.25f, box.alpha, 1e-4f)

        ModifierRegistry.tick(0.25f)
        assertEquals(0f, box.alpha, 1e-4f)
    }

    @Test
    fun sequenceStartedWhilePausedStartsOnResume() {
        val root = UIContainer()
        val hud = UIContainer()
        val box = UIBox()
        root += hud
        hud += box

        ModifierRegistry.tick(0.5f)
        ModifierRegistry.setPaused(hud, true)
        ModifierRegistry.tick(1f)

        box.fadeTo(0f, 1f)
        ModifierRegistry.tick(1f)
        assertEquals(1f, box.alpha, 1e-4f)

        ModifierRegistry.setPaused(hud, false)
        ModifierRegistry.tick(0.5f)
        assertEquals(0.5f, box.alpha, 1e-4f)

        ModifierRegistry.tick(0.5f)
        assertEquals(0f, box.alpha, 1e-4f)
    }

    @Test
    fun absoluteSequenceUnderPausedRootUsesItsTime() {
        val root = UIContainer()
        val hud = UIContainer()
        val box = UIBox()
        root += hud
        hud += box

        ModifierRegistry.setPaused(hud, true)
        ModifierRegistry.tick(1f)

        box.beginAbsoluteSequence(box.modifierStartTime + 1f) { fadeTo(0f, 1f) }
        ModifierRegistry.tick(2f)
        assertEquals(1f, box.alpha, 1e-4f)

        ModifierRegistry.setPaused(hud, false)
        ModifierRegistry.tick(1f)
        assertEquals(1f, box.alpha, 1e-4f)

        ModifierRegistry.tick(0.5f)
        assertEquals(0.5f, box.alpha, 1e-4f)

        ModifierRegistry.tick(0.5f)
        assertEquals(0f, box.alpha, 1e-4f)
    }

    @Test
    fun detachedComponentIsDropped() {
        val root = UIContainer()
        val box = UIBox()
        root += box

        box.fadeTo(0f, 10f)
        ModifierRegistry.tick(1f)
        val alphaAtDetach = box.alpha
        assertEquals(0.9f, alphaAtDetach, 1e-4f)

        box.removeSelf()
        ModifierRegistry.tick(1f)

        assertEquals(0, ModifierRegistry.activeCount())
        assertEquals(alphaAtDetach, box.alpha, 1e-4f)
    }

    @Test
    fun detachedSubtreeDescendantIsDropped() {
        val root = UIContainer()
        val hud = UIContainer()
        val box = UIBox()
        root += hud
        hud += box

        box.fadeTo(0f, 10f)
        ModifierRegistry.tick(1f)

        root -= hud
        ModifierRegistry.tick(1f)

        assertEquals(0, ModifierRegistry.activeCount())
        assertEquals(0.9f, box.alpha, 1e-4f)
    }

    @Test
    fun removeAndReAddInTheSameFrameSurvives() {
        val root = UIContainer()
        val box = UIBox()
        root += box

        box.fadeTo(0f, 2f)
        ModifierRegistry.tick(1f)

        root -= box
        root += box
        ModifierRegistry.tick(1f)

        assertEquals(0f, box.alpha, 1e-4f)
    }

    @Test
    fun childOfAnUnattachedTreeKeepsTickingOnceTheTreeIsAttached() {
        val root = UIContainer()
        val hud = UIContainer()
        val box = UIBox()
        hud += box

        box.fadeTo(0f, 1f)
        ModifierRegistry.tick(0.5f)

        root += hud
        ModifierRegistry.tick(0.5f)

        assertEquals(0f, box.alpha, 1e-4f)
    }

    @Test
    fun moveToXKeepsAFractionalY() {
        val root = UIContainer()
        val box = UIBox()
        root += box
        box.y = 0.5f.pct

        box.moveToX(10f, 1f)
        ModifierRegistry.tick(1f)

        assertEquals(0.5f.pct, box.y)
        assertEquals(10f.px, box.x)
    }

    @Test
    fun moveToXBeforeAttachKeepsY() {
        val box = UIBox()
        box.y = 20f.px

        box.moveToX(10f, 1f)
        ModifierRegistry.tick(1f)

        assertEquals(20f.px, box.y)
        assertEquals(10f.px, box.x)
    }

    @Test
    fun timeBaseAdvancesOnlyThroughTick() {
        val start = ModifierRegistry.time

        ModifierRegistry.tick(0.25f)
        ModifierRegistry.tick(0.5f)

        assertEquals(start + 0.75f, ModifierRegistry.time, 1e-4f)
    }

    @Test
    fun releaseDropsANeverAttachedSubtreeWithASelfRestartingModifier() {
        val before = ModifierRegistry.activeCount()

        val root = UIContainer()
        val text = UIBox()
        root += text

        // The replay marquee: a move that starts again in its own callback, so it never ends.
        fun loop() {
            text.x = 0f.px
            text.moveToX(-100f, 1f).after { loop() }
        }
        loop()

        ModifierRegistry.tick(2.5f)
        assertNotNull(ModifierRegistry.existingTargetFor(text))

        ModifierRegistry.release(root)

        assertEquals(before, ModifierRegistry.activeCount())
        assertNull(ModifierRegistry.existingTargetFor(text))

        ModifierRegistry.tick(1f)
        assertEquals(before, ModifierRegistry.activeCount())
    }
}
