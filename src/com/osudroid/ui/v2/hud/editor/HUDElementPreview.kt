package com.osudroid.ui.v2.hud.editor

import com.osudroid.ui.verktex.LegacyUnits
import com.osudroid.ui.verktex.LegacyUnits.vu
import com.reco1l.verktex.data.Anchor
import com.reco1l.verktex.data.Color4
import com.reco1l.verktex.data.Padding
import com.reco1l.verktex.data.fill
import com.reco1l.verktex.data.px
import com.reco1l.verktex.input.InputEvent
import com.reco1l.verktex.input.PointerEvent
import com.reco1l.verktex.time.TickingClock
import com.reco1l.verktex.ui.container.UIContainer
import com.reco1l.verktex.ui.text.UIText
import com.osudroid.ui.v2.hud.GameplayHUD
import com.osudroid.ui.v2.hud.HUDElement
import com.osudroid.ui.v2.hud.HUDElementSkinData
import com.rian.andengine.modifier.beginModifierSequence
import com.rian.andengine.modifier.clearModifiers
import com.rian.andengine.modifier.scaleTo
import kotlin.math.abs
import kotlin.math.min

class HUDElementPreview(private val element: HUDElement, val hud: GameplayHUD): UIContainer() {


    private val label = UIText().apply {
        fontSize = LegacyUnits.fontSize("smallFont").vu.px
        wrapText = false
        anchor = Anchor.BottomLeft
        origin = Anchor.BottomLeft
        text = element.name
        color = Color4.White
    }

    init {
        width = fill()
        height = 120f.vu.px
        padding = Padding(12f.vu.px)
        scaleOrigin = Anchor.Center

        backgroundColor = Color4(0xFF363653)
        radius = 12f.vu.px

        this += element
        this += label
        element.setSkinData(
            HUDElementSkinData(
                type = element::class,
                anchor = Anchor.TopLeft,
                origin = Anchor.TopLeft,
            )
        )
    }

    override fun onUpdate(clock: TickingClock) {

        // Scaling the element inside the box (the AndEngine version did it before drawing).
        element.scaleOrigin = Anchor.TopLeft

        val innerWidth = (bounds.width - padding.left.toPixels() - padding.right.toPixels()) / LegacyUnits.scale
        val innerHeight = (bounds.height - padding.top.toPixels() - padding.bottom.toPixels()) / LegacyUnits.scale
        val elementSize = element.size

        if (elementSize.x > elementSize.y) {
            element.setElementScale(min(1f, innerWidth / elementSize.x))
        } else {
            element.setElementScale(min(1f, (innerHeight - label.measuredSize.y / LegacyUnits.scale) / elementSize.y))
        }


        super.onUpdate(clock)
    }

    //region Input handling
    private var initialX = 0f
    private var initialY = 0f

    // Verktex offers releases to every child, AndEngine bound them to the pressed area.
    private var pressedPointerId = -1

    override fun onInputEvent(event: InputEvent): Boolean {

        if (event !is PointerEvent) {
            return false
        }

        val local = toLocalSpaceFromScene(event.x, event.y) / LegacyUnits.scale

        val localX = local.x
        val localY = local.y

        if (event.action == PointerEvent.Action.Down) {
            initialX = localX
            initialY = localY
            pressedPointerId = event.pointerId
        }

        if (event.action == PointerEvent.Action.Up && event.pointerId == pressedPointerId) {
            pressedPointerId = -1

            if (abs(localX - initialX) < 1f && abs(localY - initialY) < 1f) {
                clearModifiers()
                beginModifierSequence {
                    scaleTo(0.9f, 0.1f)
                    then()
                    scaleTo(1f, 0.1f)
                }
                hud.addElement(HUDElementSkinData(element::class))
                hud.elementSelector?.collapse()
            }
        }

        return false
    }
    //endregion

}