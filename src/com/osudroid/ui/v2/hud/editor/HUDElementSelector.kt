package com.osudroid.ui.v2.hud.editor

import android.graphics.PointF
import com.osudroid.ui.verktex.LegacyUnits
import com.osudroid.ui.verktex.LegacyUnits.vu
import com.reco1l.verktex.data.Anchor
import com.reco1l.verktex.data.Axis
import com.reco1l.verktex.data.Color4
import com.reco1l.verktex.data.Padding
import com.reco1l.verktex.data.fill
import com.reco1l.verktex.data.px
import com.reco1l.verktex.input.InputEvent
import com.reco1l.verktex.input.PointerEvent
import com.reco1l.verktex.ui.container.UIContainer
import com.reco1l.verktex.ui.container.UILinearContainer
import com.reco1l.verktex.ui.container.UILinearContainer.Orientation
import com.reco1l.verktex.ui.container.UIScrollableContainer
import com.reco1l.verktex.ui.text.UIText
import com.osudroid.ui.v2.hud.GameplayHUD
import com.osudroid.ui.v2.hud.HUDElements
import com.osudroid.ui.v2.hud.IGameplayEvents
import com.osudroid.beatmaps.constants.HitObjectType
import com.osudroid.beatmaps.hitobjects.HitObject
import com.rian.andengine.modifier.clearModifiers
import com.rian.andengine.modifier.modifierTarget
import com.rian.andengine.modifier.moveToX
import ru.nsu.ccfit.zuev.osu.game.GameScene
import ru.nsu.ccfit.zuev.osu.scoring.StatisticV2
import kotlin.reflect.full.primaryConstructor

class HUDElementSelector(private val hud: GameplayHUD) : UIContainer(), IGameplayEvents {


    /**
     * Whether the element selector is expanded.
     */
    val isExpanded
        get() = modifierTarget.getX() >= 0f


    private val elements = HUDElements.entries.map { it.type.primaryConstructor!!.call() }

    private val elementList = UIScrollableContainer().apply {

        scrollAxes = Axis.Y
        height = fill()
        width = SELECTOR_WIDTH.vu.px

        backgroundColor = Color4(0xFF1E1E2E)

        +UILinearContainer().apply {
            width = fill()
            padding = Padding(16f.vu.px)
            spacing = 12f.vu.px
            orientation = Orientation.Vertical

            elements.forEach { element ->
                +HUDElementPreview(element, hud)
            }
        }

    }


    init {
        height = fill()

        x = (-SELECTOR_WIDTH).vu.px

        // The button to show/hide the element selector
        +object : UIContainer() {

            // Verktex offers releases to every child, AndEngine bound them to the pressed area.
            private var pressedPointerId = -1

            init {
                backgroundColor = Color4(0xFF181825)
                radius = BUTTON_RADIUS.vu.px

                width = BUTTON_WIDTH.vu.px
                height = 150f.vu.px
                x = (SELECTOR_WIDTH - BUTTON_RADIUS).vu.px

                anchor = Anchor.CenterLeft
                origin = Anchor.CenterLeft

                +UIText().apply {
                    rotationZ = -90f
                    anchor = Anchor.Center
                    origin = Anchor.Center
                    fontSize = LegacyUnits.fontSize("smallFont").vu.px
                    wrapText = false
                    text = "Elements"

                    x = (BUTTON_RADIUS / 2).vu.px
                }
            }

            override fun onInputEvent(event: InputEvent): Boolean {

                if (event !is PointerEvent) {
                    return false
                }

                if (event.action == PointerEvent.Action.Down) {
                    pressedPointerId = event.pointerId
                    return true
                }

                if (event.action == PointerEvent.Action.Up && event.pointerId == pressedPointerId) {
                    pressedPointerId = -1

                    if (isExpanded) {
                        collapse()
                    } else {
                        expand()
                    }
                    return false
                }
                return false
            }

        }

        +elementList
    }


    fun expand() {
        if (isExpanded) {
            return
        }

        hud.selected = null

        clearModifiers()
        elementList.isVisible = true
        moveToX(0f, 0.2f)
    }

    fun collapse() {
        if (!isExpanded) {
            return
        }
        clearModifiers()
        moveToX((-SELECTOR_WIDTH).vu, 0.2f).after { elementList.isVisible = false }
    }


    //region Gameplay Events

    override fun onNoteHit(statistics: StatisticV2) {
        elements.forEach { it.onNoteHit(statistics) }
    }

    override fun onBreakStateChange(isBreak: Boolean) {
        elements.forEach { it.onBreakStateChange(isBreak) }
    }

    override fun onGameplayUpdate(gameScene: GameScene, secondsElapsed: Float) {
        elements.forEach { it.onGameplayUpdate(gameScene, secondsElapsed) }
    }

    override fun onGameplayTouchDown(time: Float) {
        elements.forEach { it.onGameplayTouchDown(time) }
    }

    override fun onHitObjectLifetimeStart(obj: HitObject) {
        elements.forEach { it.onHitObjectLifetimeStart(obj) }
    }

    override fun onAccuracyRegister(type: HitObjectType, accuracy: Float) {
        elements.forEach { it.onAccuracyRegister(type, accuracy) }
    }

    override fun onAimJudgement(objectPosition: PointF, cursorPosition: PointF, objectRadius: Float) {
        elements.forEach { it.onAimJudgement(objectPosition, cursorPosition, objectRadius) }
    }

    override fun onSeek() {
        elements.forEach { it.onSeek() }
    }

    //endregion


    companion object {

        const val SELECTOR_WIDTH = 340f
        const val BUTTON_WIDTH = 48f
        const val BUTTON_RADIUS = 12f

    }

}
