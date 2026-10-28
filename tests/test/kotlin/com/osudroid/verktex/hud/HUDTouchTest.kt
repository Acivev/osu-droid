package com.osudroid.verktex.hud

import com.osudroid.ui.v2.hud.GameplayHUD
import com.osudroid.ui.v2.hud.HUDElement
import com.osudroid.ui.v2.hud.HUDElementSkinData
import com.osudroid.verktex.VerktexTestFixtures
import com.reco1l.verktex.data.Anchor
import com.reco1l.verktex.data.px
import com.reco1l.verktex.input.PointerEvent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import ru.nsu.ccfit.zuev.osu.Config
import ru.nsu.ccfit.zuev.osu.game.GameScene

class HUDTouchTest {

    /**
     * A counter-sized element: the real counters need skin textures and fonts, which the JVM has not.
     */
    class FixedSizeElement : HUDElement() {
        init {
            width = 200f.px
            height = 50f.px
        }
    }

    @Before
    fun setUp() {
        VerktexTestFixtures.ensureInitialized()

        Config.setRES_WIDTH(1280)
        Config.setRES_HEIGHT(720)
    }

    private fun down(x: Float, y: Float, pointerId: Int = 0) =
        PointerEvent(PointerEvent.Action.Down, PointerEvent.Button.Left, pointerId, x, y)

    @Test
    fun hudDoesNotConsumeTouchesOutsideEditMode() {
        val hud = GameplayHUD().apply { sceneCheck = { true } }

        hud.addElement(
            HUDElementSkinData(
                type = FixedSizeElement::class,
                anchor = Anchor.Center,
                origin = Anchor.Center
            ),
            inEditMode = false
        )
        layoutOverWindow(hud)

        // The press is over the element, which takes nothing while the editor is off.
        assertFalse(hud.onInputEvent(down(640f, 360f)))
        assertNull(hud.selected)
    }

    @Test
    fun blockAreaMarksTheCursorWithoutConsuming() {
        val pressed = mutableListOf<Int>()
        val box = GameScene.BlockAreaBox(100f, 100f, 200f, 100f) { pressed += it }

        layoutOverWindow(box)

        assertFalse(box.onInputEvent(down(150f, 150f, pointerId = 2)))
        assertEquals(listOf(2), pressed)

        // Only presses mark the cursor.
        assertFalse(box.onInputEvent(PointerEvent(PointerEvent.Action.Up, PointerEvent.Button.Left, 2, 150f, 150f)))
        assertEquals(listOf(2), pressed)
    }
}
