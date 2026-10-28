package com.osudroid.ui.v2.hud.elements

import com.edlplan.framework.easing.Easing
import com.osudroid.ui.verktex.LegacyUnits.vu
import com.osudroid.ui.verktex.SkinTextures
import com.reco1l.verktex.data.Anchor
import com.reco1l.verktex.data.Color4
import com.reco1l.verktex.data.fill
import com.reco1l.verktex.data.px
import com.reco1l.verktex.graphics.CompareFunction
import com.reco1l.verktex.graphics.DepthState
import com.reco1l.verktex.input.InputEvent
import com.reco1l.verktex.input.PointerEvent
import com.reco1l.verktex.time.TickingClock
import com.reco1l.verktex.ui.UISprite
import com.reco1l.verktex.ui.shape.UICircle
import com.reco1l.framework.Interpolation
import com.osudroid.ui.v2.hud.HUDElement
import ru.nsu.ccfit.zuev.osu.Config
import ru.nsu.ccfit.zuev.osu.GlobalManager

class HUDBackButton : HUDElement() {

    override val shouldBeShown = true

    private val requiredPressTimeMs = Config.getBackButtonPressTime().toFloat()

    private val arrow = UISprite().apply {
        textureRegion = SkinTextures.getAsset("back-arrow", "back-arrow.png")
        scaleType = UISprite.ScaleType.Stretch
        anchor = Anchor.Center
        origin = Anchor.Center

        width = fill(0.6f)
        height = fill(0.6f)
    }

    private val backCircle = UICircle().apply {
        setPortion(0f)
        anchor = Anchor.Center
        origin = Anchor.Center
        color = Color4.White
        depthState = DEPTH_TEST_LESS

        width = fill(1f)
        height = fill(1f)
    }

    private val frontCircle = UICircle().apply {
        anchor = Anchor.Center
        origin = Anchor.Center
        color = Color4(0xFF002626)
        clearDepthBeforeDraw = true
        depthState = DEPTH_TEST_LESS

        width = fill(0.95f)
        height = fill(0.95f)
    }


    private var holdDurationMs = 0f
        set(value) {
            field = value.coerceIn(0f, requiredPressTimeMs)

            // The progress is intentionally kept at 0 for 0 activation time to keep the button in its initial state.
            val progress = if (requiredPressTimeMs > 0) field / requiredPressTimeMs else 0f
            val scale = 1f + progress / 2f

            alpha = Interpolation.floatAt(holdDurationMs, 0.25f, 0.5f, 0f, requiredPressTimeMs, Easing.OutCubic)
            backCircle.setPortion(progress)
            backCircle.scale = scale
            frontCircle.scale = scale
            arrow.scale = scale
        }


    private var isPressed = false

    // Verktex offers moves and releases to every child, AndEngine bound them to the pressed area.
    private var pressedPointerId = -1


    init {
        width = SIZE.vu.px
        height = SIZE.vu.px
        alpha = 0.25f

        // The HUD layer may be attached to the beatmap's clock, which can be paused at any time. When that happens,
        // this back button will never be pressable. The Verktex gameplay layer always runs on the engine's update
        // clock, so the press duration is based on real time, not game time.

        this += frontCircle
        this += backCircle
        this += arrow
    }


    override fun onUpdate(clock: TickingClock) {

        if (!isInEditMode) {
            val realMsElapsed = clock.elapsedTickTime * 1000

            if (isPressed) {
                holdDurationMs += realMsElapsed

                if (holdDurationMs >= requiredPressTimeMs) {
                    isPressed = false
                    GlobalManager.getInstance().gameScene.pause()
                }
            } else {
                holdDurationMs -= realMsElapsed * 2
            }
        }

        super.onUpdate(clock)
    }

    override fun onInputEvent(event: InputEvent): Boolean {

        if (isInEditMode) {
            return super.onInputEvent(event)
        }

        if (event !is PointerEvent) {
            return false
        }

        if (event.action == PointerEvent.Action.Down) {
            isPressed = true
            pressedPointerId = event.pointerId
            return true
        }

        if (event.pointerId != pressedPointerId) {
            return false
        }

        if (event.action == PointerEvent.Action.Move) {
            if (isPressed) {
                val local = toLocalSpaceFromScene(event.x, event.y)

                if (local.x <= 0f || local.y <= 0f || local.x >= bounds.width || local.y >= bounds.height) {
                    isPressed = false
                }
                return true
            }
        }

        if (event.action == PointerEvent.Action.Up || event.action == PointerEvent.Action.Cancel) {
            if (isPressed) {
                isPressed = false
                return true
            }
        }

        return false
    }


    companion object {
        private const val SIZE = 72f

        // The AndEngine DepthInfo.Default and DepthInfo.Less: depth test and write with GL_LESS.
        private val DEPTH_TEST_LESS = DepthState(enabled = true, writeEnabled = true, function = CompareFunction.Less)
    }
}