package com.osudroid.ui.v2.hud.editor

import com.osudroid.ui.verktex.LegacyUnits
import com.osudroid.ui.verktex.LegacyUnits.vu
import com.osudroid.ui.verktex.SkinTextures
import com.reco1l.verktex.Verktex
import com.reco1l.verktex.data.Anchor
import com.reco1l.verktex.data.Color4
import com.reco1l.verktex.data.Vec2
import com.reco1l.verktex.data.fill
import com.reco1l.verktex.data.px
import com.reco1l.verktex.graphics.GraphicsRenderer
import com.reco1l.verktex.input.InputEvent
import com.reco1l.verktex.input.PointerEvent
import com.reco1l.verktex.time.TickingClock
import com.reco1l.verktex.ui.UICanvas
import com.reco1l.verktex.ui.UISprite
import com.reco1l.verktex.ui.container.UIConstraintContainer
import com.reco1l.verktex.ui.container.UIContainer
import com.reco1l.verktex.ui.container.UILinearContainer
import com.reco1l.verktex.ui.container.UILinearContainer.Orientation
import com.reco1l.verktex.ui.shape.*
import com.reco1l.verktex.ui.text.*
import com.osudroid.ui.v2.hud.HUDElement
import com.osudroid.utils.updateThread
import com.rian.andengine.modifier.beginModifierSequence
import com.rian.andengine.modifier.clearModifiers
import com.rian.andengine.modifier.scaleTo

class HUDElementOverlay(private val element: HUDElement) : UIConstraintContainer() {


    /**
     * The position of the outline box that represents the element, in virtual units.
     */
    val outlinePosition
        get() = Vec2(outlineX, outlineY)

    private var outlineX = 0f
    private var outlineY = 0f


    private val outline = OutlineBox().apply {
        color = Color4(0xFFF27272)
        // The AndEngine outline width was in window pixels.
        lineWidth = 8f
    }

    private val toolbar = UILinearContainer().apply {
        anchor = Anchor.TopCenter
        origin = Anchor.BottomCenter
        orientation = Orientation.Horizontal
        spacing = 4f.vu.px
        y = (-10f).vu.px

        // Toolbar buttons:

        +Button("delete", Color4(0xFF260000)) {
            updateThread {
                Verktex.withTreeLock { element.remove() }
            }
        }

        +Button("restore") {
            element.restore()
        }

    }

    private val nameText = UIText().apply {
        anchor = Anchor.BottomCenter
        origin = Anchor.TopCenter
        fontSize = LegacyUnits.fontSize("smallFont").vu.px
        wrapText = false
        color = Color4(0xFFF27272)
        text = element.name
    }


    init {
        alpha = 0f
        width = fill()
        height = fill()

        this += outline
        this += toolbar
        this += nameText

        addConstraint(toolbar, outline)
        addConstraint(nameText, outline)

        val topLeftTip = Tip().apply { anchor = Anchor.BottomLeft }
        val topRightTip = Tip().apply { anchor = Anchor.BottomRight }
        val bottomLeftTip = Tip().apply { anchor = Anchor.TopLeft }
        val bottomRightTip = Tip().apply { anchor = Anchor.TopRight }

        this += topLeftTip
        this += topRightTip
        this += bottomLeftTip
        this += bottomRightTip

        addConstraint(topLeftTip, outline)
        addConstraint(topRightTip, outline)
        addConstraint(bottomLeftTip, outline)
        addConstraint(bottomRightTip, outline)
    }


    /**
     * Updates the outline box to match the element's position and size.
     */
    fun updateOutline() {
        val transformedWidth = element.size.x * element.scaleX
        val transformedHeight = element.size.y * element.scaleY

        // We need to cancel scale center
        outlineX = element.anchorPosition.x + element.position.x - (transformedWidth * element.origin.x)
        outlineY = element.anchorPosition.y + element.position.y - (transformedHeight * element.origin.y)

        outline.x = outlineX.vu.px
        outline.y = outlineY.vu.px
        outline.width = transformedWidth.vu.px
        outline.height = transformedHeight.vu.px
    }


    override fun onInputEvent(event: InputEvent): Boolean {
        if (alpha == 1f) {
            return super.onInputEvent(event)
        }
        return false
    }

    override fun onUpdate(clock: TickingClock) {
        updateOutline()

        // Ensure that the toolbar does not go out of the screen.
        if (outlineY - toolbar.measuredSize.y / LegacyUnits.scale < 0f) {
            toolbar.anchor = Anchor.BottomCenter
            toolbar.origin = Anchor.TopCenter
        } else {
            toolbar.anchor = Anchor.TopCenter
            toolbar.origin = Anchor.BottomCenter
        }

        // Show only the tips that are not at the origin.
        forEach {
            if (it is Tip) {
                it.isVisible = it.anchor.x != element.origin.x && it.anchor.y != element.origin.y
            }
        }

        super.onUpdate(clock)
    }


    /**
     * The AndEngine outline box: a line [lineWidth] window pixels wide along the edges, half of it
     * inside and half outside.
     */
    private class OutlineBox : UICanvas() {

        var lineWidth = 1f

        override fun onDraw(renderer: GraphicsRenderer) {
            val half = lineWidth / 2f
            val width = bounds.width
            val height = bounds.height

            renderer.drawRect(-half, -half, width + lineWidth, lineWidth, color = contentColor)
            renderer.drawRect(-half, height - half, width + lineWidth, lineWidth, color = contentColor)
            renderer.drawRect(-half, half, lineWidth, height - lineWidth, color = contentColor)
            renderer.drawRect(width - half, half, lineWidth, height - lineWidth, color = contentColor)
        }
    }


    /**
     * Represents a tip in the overlay toolbar.
     */
    private inner class Tip : UIContainer() {

        init {
            origin = Anchor.Center
            width = TIP_SIZE.vu.px
            height = TIP_SIZE.vu.px

            // The tip container is bigger than the actual tip in order to make it easier to touch.
            +UIBox().apply {
                anchor = Anchor.Center
                origin = Anchor.Center
                backgroundColor = Color4(0xFFF27272)
                radius = TIP_SIZE.vu.px
                width = fill(0.5f)
                height = fill(0.5f)
            }
        }


        private var initialX = 0f
        private var initialY = 0f

        // Verktex offers moves to every child, AndEngine bound them to the pressed area.
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
                return true
            }

            if (event.pointerId != pressedPointerId) {
                return false
            }

            if (event.action == PointerEvent.Action.Move) {
                var deltaX = localX - initialX
                var deltaY = localY - initialY

                if (anchor.x == 0f) {
                    deltaX = -deltaX
                }

                if (anchor.y == 0f) {
                    deltaY = -deltaY
                }

                val deltaScaleX = deltaX / (element.size.x * element.scaleX)
                val deltaScaleY = deltaY / (element.size.y * element.scaleY)

                val scaleX = (element.scaleX + deltaScaleX).coerceIn(0.5f, 5f)
                val scaleY = (element.scaleY + deltaScaleY).coerceIn(0.5f, 5f)

                element.setElementScale((scaleX + scaleY) / 2f)
                return true
            }

            if (event.action == PointerEvent.Action.Up || event.action == PointerEvent.Action.Cancel) {
                pressedPointerId = -1
            }

            return false
        }
    }


    /**
     * Represents a button in the overlay toolbar.
     */
    private inner class Button(texture: String, back: Color4 = Color4(0xFF002626), val action: () -> Unit) : UIContainer() {

        // Verktex offers releases to every child, AndEngine bound them to the pressed area.
        private var pressedPointerId = -1

        init {
            width = BUTTON_SIZE.vu.px
            height = BUTTON_SIZE.vu.px
            scaleOrigin = Anchor.Center

            +UIBox().apply {
                radius = 12f.vu.px
                backgroundColor = back
                width = fill()
                height = fill()
            }

            +UISprite().apply {
                textureRegion = SkinTextures.getAsset(texture, "$texture.png")
                scaleType = UISprite.ScaleType.Stretch
                anchor = Anchor.Center
                origin = Anchor.Center
                width = fill(0.8f)
                height = fill(0.8f)
            }
        }

        override fun onInputEvent(event: InputEvent): Boolean {
            if (event !is PointerEvent) {
                return false
            }

            if (event.action == PointerEvent.Action.Up && event.pointerId == pressedPointerId) {
                pressedPointerId = -1
                action()
                clearModifiers()
                beginModifierSequence {
                    scaleTo(0.9f, 0.1f)
                    then()
                    scaleTo(1f, 0.1f)
                }
                return true
            }

            if (event.action == PointerEvent.Action.Down) {
                pressedPointerId = event.pointerId
                return true
            }

            if (event.action == PointerEvent.Action.Cancel && event.pointerId == pressedPointerId) {
                pressedPointerId = -1
            }

            return false
        }
    }


    companion object {
        private const val TIP_SIZE = 36f
        private const val BUTTON_SIZE = 46f
    }

}