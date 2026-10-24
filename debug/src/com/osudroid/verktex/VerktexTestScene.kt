package com.osudroid.verktex

import android.os.SystemClock
import android.view.MotionEvent
import com.reco1l.verktex.Verktex
import com.reco1l.verktex.android.AndroidGameHost
import com.reco1l.verktex.audio.Sounds
import com.reco1l.verktex.data.Anchor
import com.reco1l.verktex.data.Axis
import com.reco1l.verktex.data.ClipMode
import com.reco1l.verktex.data.Color4
import com.reco1l.verktex.data.Padding
import com.reco1l.verktex.data.Vec2
import com.reco1l.verktex.data.dip
import com.reco1l.verktex.data.fill
import com.reco1l.verktex.texture.Textures
import com.reco1l.verktex.time.TickingClock
import com.reco1l.verktex.ui.FAIcon
import com.reco1l.verktex.ui.UIBadge
import com.reco1l.verktex.ui.UIButton
import com.reco1l.verktex.ui.UIComponent
import com.reco1l.verktex.ui.Theme
import com.reco1l.verktex.ui.UILoadingIndicator
import com.reco1l.verktex.ui.UIScene
import com.reco1l.verktex.ui.UISprite
import com.reco1l.verktex.ui.badge
import com.reco1l.verktex.ui.card
import com.reco1l.verktex.ui.container.UIContainer
import com.reco1l.verktex.ui.container.UILinearContainer
import com.reco1l.verktex.ui.container.container
import com.reco1l.verktex.ui.container.linearContainer
import com.reco1l.verktex.ui.container.scrollableContainer
import com.reco1l.verktex.ui.control.UICheckbox
import com.reco1l.verktex.ui.control.UISelect
import com.reco1l.verktex.ui.gradientBox
import com.reco1l.verktex.ui.icon
import com.reco1l.verktex.ui.iconButton
import com.reco1l.verktex.ui.loadingIndicator
import com.reco1l.verktex.ui.shape.UIBox
import com.reco1l.verktex.ui.shape.box
import com.reco1l.verktex.ui.shape.circle
import com.reco1l.verktex.ui.shape.line
import com.reco1l.verktex.ui.shape.triangle
import com.reco1l.verktex.ui.sprite
import com.reco1l.verktex.ui.text.faSolidIcon
import com.reco1l.verktex.ui.text.text
import com.reco1l.verktex.ui.textButton
import kotlin.math.PI
import kotlin.math.sin

class VerktexTestScene : UIScene() {

    private var clicks = 0

    init {
        backgroundColor = Color4(0xFF1E1E2E)

        scrollableContainer {
            width = fill()
            height = fill()
            scrollAxes = Axis.Y

            linearContainer {
                orientation = UILinearContainer.Orientation.Vertical
                width = fill()
                spacing = 16f.dip
                padding = Padding(24f.dip)

                text {
                    text = "Verktex test scene"
                    fontSize = 28f.dip
                }

                section("Theme") {
                    row {
                        for (scale in listOf(1f, 1.3f)) {
                            textButton {
                                text = "Font scale $scale"
                                onActionUp = { Verktex.fontScale = scale }
                            }
                        }
                        for ((name, accent) in listOf("Default accent" to Theme.DEFAULT_ACCENT_COLOR, "Green accent" to Color4(0xFFA6E3A1))) {
                            textButton {
                                text = name
                                onActionUp = { Verktex.theme = Theme(palette = Theme.ColorPalette(accent)) }
                            }
                        }
                    }
                }

                section("Buttons") {
                    val counter = text { text = "Button clicks: 0" }

                    row {
                        for (variant in UIButton.Variant.entries) {
                            textButton {
                                text = variant.name
                                this.variant = variant
                                onActionUp = {
                                    clicks++
                                    counter.text = "Button clicks: $clicks"
                                }
                            }
                        }
                    }

                    row {
                        for (density in UIButton.Density.entries) {
                            textButton {
                                text = density.name
                                this.density = density
                            }
                        }
                        textButton {
                            text = "Disabled"
                            isEnabled = false
                        }
                        textButton {
                            text = "Selected"
                            isSelected = true
                        }
                        textButton {
                            text = "With icons"
                            leadingIcon = faSolidIcon(FAIcon.Play)
                            trailingIcon = faSolidIcon(FAIcon.Heart)
                        }
                    }

                    row {
                        for (code in listOf(FAIcon.Gear, FAIcon.Music, FAIcon.Star, FAIcon.Heart)) {
                            iconButton { icon = code }
                        }
                    }
                }

                section("Text") {
                    text {
                        text = "Bold torus font"
                        fontFamily = com.reco1l.verktex.fonts.Fonts["torus"]!!
                    }
                    text {
                        text = "Small text"
                        fontSize = 12f.dip
                    }
                    text {
                        text = "Large text"
                        fontSize = 32f.dip
                    }
                    text {
                        text = "Colored text"
                        color = Color4(0xFFF38BA8)
                    }
                    text {
                        text = "Leading and trailing icons"
                        leadingIcon = faSolidIcon(FAIcon.Star)
                        trailingIcon = faSolidIcon(FAIcon.Check)
                        iconSpacing = 8f.dip
                    }
                    text {
                        width = 300f.dip
                        text = "This is a long paragraph that should wrap inside a fixed width of 300dip, " +
                            "testing word wrapping across several lines."
                    }
                    text {
                        width = 300f.dip
                        maxLines = 1
                        text = "This line is truncated because maxLines is set to one and the text is long."
                    }
                    text {
                        width = 300f.dip
                        backgroundColor = SURFACE_COLOR
                        alignment = Anchor.TopRight
                        text = "Right aligned"
                    }
                    text {
                        text = "Umlaute: äöüß ÄÖÜ €"
                    }
                    text {
                        width = 300f.dip
                        alignment = Anchor.TopCenter
                        backgroundColor = SURFACE_COLOR
                        text = "Centered text\nwith a manual line break\nand a third line"
                    }
                    text {
                        width = 200f.dip
                        wrapText = false
                        clipping = ClipMode.Bounds
                        autoScrollAxes = Axis.X
                        autoScrollSpeed = 150f
                        autoScrollTimeout = 1f
                        backgroundColor = SURFACE_COLOR
                        text = "This text is too long for its box, so it scrolls automatically."
                    }
                }

                section("Icons and badges") {
                    row {
                        for (code in listOf(FAIcon.Gear, FAIcon.Music, FAIcon.Star, FAIcon.Heart, FAIcon.Play)) {
                            icon { iconCode = code }
                        }
                    }
                    row {
                        for (variant in UIBadge.Variant.entries) {
                            badge {
                                text = variant.name
                                this.variant = variant
                            }
                        }
                    }
                }

                section("Shapes") {
                    row {
                        box {
                            width = 64f.dip
                            height = 64f.dip
                            backgroundColor = Color4(0xFF89B4FA)
                        }
                        box {
                            width = 64f.dip
                            height = 64f.dip
                            radius = 16f.dip
                            backgroundColor = Color4(0xFFA6E3A1)
                        }
                        box {
                            width = 64f.dip
                            height = 64f.dip
                            radius = 8f.dip
                            borderWidth = 3f.dip
                            borderColor = Color4(0xFFF9E2AF)
                            backgroundColor = SURFACE_COLOR
                        }
                        box {
                            width = 64f.dip
                            height = 64f.dip
                            radius = 32f.dip
                            backgroundColor = Color4(0x80F38BA8)
                        }
                        circle {
                            width = 64f.dip
                            height = 64f.dip
                            color = Color4(0xFFCBA6F7)
                        }
                        circle {
                            width = 64f.dip
                            height = 64f.dip
                            color = Color4(0xFFFAB387)
                            setPortion(0.75f)
                        }
                        triangle {
                            width = 64f.dip
                            height = 64f.dip
                            color = Color4(0xFF94E2D5)
                        }
                        circle {
                            width = 64f.dip
                            height = 64f.dip
                            color = Color4(0xFFFAB387)
                            startAngle = 90f
                            setPortion(-0.5f)
                        }
                        triangle {
                            width = 64f.dip
                            height = 64f.dip
                            middle = 0f
                            color = Color4(0xFF94E2D5)
                        }
                        container {
                            width = 64f.dip
                            height = 64f.dip
                            line {
                                fromPoint = Vec2(0f, 0f)
                                toPoint = Vec2(64f.dip.toPixels(), 64f.dip.toPixels())
                                lineWidth = 3f.dip
                                color = Color4(0xFFF5C2E7)
                            }
                        }
                    }
                }

                section("Gradients and loading indicators") {
                    row {
                        for (angle in listOf(0f, 90f, 45f)) {
                            gradientBox {
                                width = 120f.dip
                                height = 64f.dip
                                gradientAngle = angle
                                colorStart = Color4(0xFF89B4FA)
                                colorEnd = Color4(0xFFF38BA8)
                            }
                        }
                        gradientBox {
                            width = 120f.dip
                            height = 64f.dip
                            radius = 16f.dip
                            borderWidth = 2f.dip
                            borderColor = Color4(0xFFF9E2AF)
                            colorStart = Color4(0xFFA6E3A1)
                            colorEnd = Color4(0x00A6E3A1)
                        }
                    }
                    val overlay = UILoadingIndicator().apply {
                        width = 96f.dip
                        height = 96f.dip
                        anchor = Anchor.Center
                        origin = Anchor.Center
                    }
                    textButton {
                        text = "Toggle overlay"
                        onActionUp = {
                            if (overlay.parent == null) Verktex.addOverlay(overlay) else Verktex.removeOverlay(overlay)
                        }
                    }

                    row {
                        loadingIndicator()
                        loadingIndicator {
                            width = 64f.dip
                            height = 64f.dip
                            thickness = 6f.dip
                            rotationSpeed = 180f
                            arcColor = Color4(0xFFF38BA8)
                        }
                    }
                }

                section("Transformations and animation") {
                    row {
                        +SpinningBox(Color4(0xFF89B4FA), degreesPerSecond = 90f)
                        +SpinningBox(Color4(0xFFA6E3A1), degreesPerSecond = -180f)
                        +OscillatingBox(Color4(0xFFF9E2AF)) { scale = 0.5f + 0.5f * it }
                        +OscillatingBox(Color4(0xFFF38BA8)) { alpha = 0.2f + 0.8f * it }
                        +OscillatingBox(Color4(0xFFCBA6F7)) { translationY = fill(it * 0.5f - 0.25f) }
                    }
                }

                section("Clipping") {
                    container {
                        width = 200f.dip
                        height = 64f.dip
                        clipping = ClipMode.Bounds
                        backgroundColor = SURFACE_COLOR
                        box {
                            width = 120f.dip
                            height = 120f.dip
                            x = 140f.dip
                            backgroundColor = Color4(0xFFF38BA8)
                        }
                        text {
                            text = "Clipped container, red box must be cut off"
                        }
                    }
                }

                section("Nested transforms") {
                    val nestedLabel = text { text = "Nested clicks: 0, long presses: 0" }
                    var nestedClicks = 0
                    var longPresses = 0

                    fun updateNestedLabel() {
                        nestedLabel.text = "Nested clicks: $nestedClicks, long presses: $longPresses"
                    }

                    row {
                        spacing = 48f.dip

                        container {
                            width = 220f.dip
                            height = 120f.dip
                            radius = 8f.dip
                            rotationZ = 15f
                            backgroundColor = SURFACE_COLOR

                            textButton {
                                text = "Rotated"
                                anchor = Anchor.Center
                                origin = Anchor.Center
                                onActionUp = { nestedClicks++; updateNestedLabel() }
                            }
                        }

                        container {
                            width = 220f.dip
                            height = 120f.dip
                            radius = 8f.dip
                            scale = 0.7f
                            backgroundColor = SURFACE_COLOR

                            textButton {
                                text = "Scaled"
                                anchor = Anchor.Center
                                origin = Anchor.Center
                                onActionUp = { nestedClicks++; updateNestedLabel() }
                            }
                        }

                        textButton {
                            text = "Long press me"
                            onActionLongPress = { longPresses++; updateNestedLabel() }
                        }
                    }
                }

                section("Multi-touch") {
                    text { text = "Simulates finger A holding the target while finger B taps elsewhere, then A releases on the target." }

                    val resultLabel = text { text = "Target clicks: 0 (expected 1 per run)" }
                    var targetClicks = 0

                    row {
                        val target = textButton {
                            text = "Target"
                            onActionUp = {
                                targetClicks++
                                resultLabel.text = "Target clicks: $targetClicks (expected 1 per run)"
                            }
                        }
                        textButton {
                            text = "Run test"
                            onActionUp = { simulateSecondFingerTap(target) }
                        }
                    }
                }

                section("Nested clipping and scrolling") {
                    text { text = "The inner list must stay inside the outer box:" }

                    container {
                        width = 300f.dip
                        height = 150f.dip
                        clipping = ClipMode.Bounds
                        backgroundColor = SURFACE_COLOR

                        // Taller than its parent, so its own clipping must be cut by the parent one.
                        scrollableContainer {
                            width = fill()
                            height = 260f.dip
                            scrollAxes = Axis.Y
                            clipping = ClipMode.Bounds
                            backgroundColor = Color4(0x40F38BA8)

                            linearContainer {
                                orientation = UILinearContainer.Orientation.Vertical
                                padding = Padding(8f.dip)
                                for (i in 1..15) {
                                    text { text = "Inner row $i" }
                                }
                            }
                        }
                    }

                    text { text = "Horizontal scrolling:" }

                    scrollableContainer {
                        width = 300f.dip
                        height = 64f.dip
                        scrollAxes = Axis.X
                        clipping = ClipMode.Bounds
                        backgroundColor = SURFACE_COLOR

                        row {
                            for (i in 0 until 12) {
                                box {
                                    width = 56f.dip
                                    height = 56f.dip
                                    radius = 8f.dip
                                    backgroundColor = if (i % 2 == 0) Color4(0xFF89B4FA) else Color4(0xFFA6E3A1)
                                }
                            }
                        }
                    }
                }

                section("Sprite") {
                    val logo = Textures.load("test-logo", "logo.png", isInternal = true)

                    if (logo == null) {
                        text { text = "Failed to load logo.png" }
                        return@section
                    }

                    fun UIContainer.logoSprite(width: Float, height: Float, block: UISprite.() -> Unit = {}) = sprite {
                        textureRegion = logo
                        this.width = width.dip
                        this.height = height.dip
                        backgroundColor = SURFACE_COLOR
                        block()
                    }

                    text { text = "Crop, fit and stretch in a wide box:" }
                    row {
                        for (type in UISprite.ScaleType.entries) {
                            logoSprite(160f, 64f) { scaleType = type }
                        }
                    }

                    text { text = "Fit aligned to the left and right, crop aligned to the top:" }
                    row {
                        logoSprite(160f, 64f) { gravity = Anchor.CenterLeft }
                        logoSprite(160f, 64f) { gravity = Anchor.CenterRight }
                        logoSprite(160f, 64f) {
                            scaleType = UISprite.ScaleType.Crop
                            gravity = Anchor.TopCenter
                        }
                    }

                    text { text = "Atlas repacking, the logo must stay intact and play.png only fits after back.png is unloaded:" }
                    row {
                        // Room for the logo and one of the wide buttons below it, but not both buttons.
                        val atlas = Verktex.graphics.createTexture(600f, 700f)
                        val logoInAtlas = Textures.load("test-atlas-logo", "logo.png", isInternal = true, destinationAtlas = atlas)!!
                        val back = Textures.load("test-atlas-back", "back.png", isInternal = true, destinationAtlas = atlas)!!

                        sprite {
                            textureRegion = logoInAtlas
                            width = 96f.dip
                            height = 96f.dip
                            backgroundColor = SURFACE_COLOR
                        }
                        val swapped = sprite {
                            textureRegion = back
                            width = 300f.dip
                            height = 46f.dip
                            backgroundColor = SURFACE_COLOR
                        }

                        textButton {
                            text = "Swap texture"
                            onActionUp = {
                                Textures.unload("test-atlas-back")
                                swapped.textureRegion = Textures.load("test-atlas-play", "play.png", isInternal = true, destinationAtlas = atlas)
                            }
                        }
                    }

                    text { text = "Normal, flipped horizontally, vertically and both, tinted:" }
                    row {
                        logoSprite(64f, 64f)
                        logoSprite(64f, 64f) { flippedHorizontal = true }
                        logoSprite(64f, 64f) { flippedVertical = true }
                        logoSprite(64f, 64f) {
                            flippedHorizontal = true
                            flippedVertical = true
                        }
                        logoSprite(64f, 64f) { color = Color4(0xFF89B4FA) }
                    }
                }

                section("Controls") {
                    val checkboxLabel = text { text = "Checkbox: false" }
                    +UICheckbox().apply {
                        onValueChange = { checkboxLabel.text = "Checkbox: $it" }
                    }

                    val multiSelectLabel = text { text = "Multi select: none" }
                    +UISelect<Int>().apply {
                        width = 240f.dip
                        selectionMode = UISelect.SelectionMode.Multiple
                        options = (1..5).map { UISelect.Option(it, "Item $it") }
                        onValueChange = { multiSelectLabel.text = "Multi select: $it" }
                    }

                    val longSelectLabel = text { text = "Long select: none" }
                    +UISelect<Int>().apply {
                        width = 240f.dip
                        options = (1..40).map { UISelect.Option(it, "Entry $it") }
                        onValueChange = { longSelectLabel.text = "Long select: $it" }
                    }

                    val selectLabel = text { text = "Select: none" }
                    +UISelect<Int>().apply {
                        width = 240f.dip
                        options = (1..5).map { UISelect.Option(it, "Option $it") }
                        onValueChange = { selectLabel.text = "Select: $it" }
                    }
                }

                section("Audio") {
                    val sample = Sounds.loadSample("test-click", "sfx/click-short-confirm.ogg", isExternal = false)
                    val stream = Sounds.loadStream("test-applause", "sfx/applause.ogg", isExternal = false)

                    val audioLabel = text { text = "Devices: ${Verktex.audio.devices.size}, active: ${Verktex.audio.activeDevice?.name}" }

                    row {
                        textButton {
                            text = "Play sample"
                            onActionUp = { sample.play() }
                        }
                        textButton {
                            text = "Play stream"
                            onActionUp = {
                                stream.position = 0.0
                                stream.play()
                                audioLabel.text = "Stream playing: ${stream.isPlaying}, length: ${"%.2f".format(stream.length)}s"
                            }
                        }
                        textButton {
                            text = "Seek near the end"
                            onActionUp = {
                                stream.position = stream.length - 1.0
                                audioLabel.text = "Stream position: ${"%.2f".format(stream.position)}s of ${"%.2f".format(stream.length)}s"
                            }
                        }
                    }
                }

                section("Card") {
                    card {
                        width = 360f.dip
                        title = "Collapsible card"
                        isCollapsible = true
                        content {
                            text { text = "Tap the header to collapse." }
                            text { text = "Second line of content." }
                        }
                    }
                }

                section("Scroll") {
                    for (i in 1..20) {
                        text { text = "Scroll row $i" }
                    }
                }
            }
        }
    }


    private inline fun UIContainer.section(title: String, block: UILinearContainer.() -> Unit) {
        linearContainer {
            orientation = UILinearContainer.Orientation.Vertical
            width = fill()
            spacing = 8f.dip
            padding = Padding(12f.dip)
            radius = 8f.dip
            backgroundColor = Color4(0xFF181825)

            text {
                text = title
                color = Color4(0xFF89B4FA)
            }
            block()
        }
    }

    private inline fun UIContainer.row(block: UILinearContainer.() -> Unit) {
        linearContainer {
            orientation = UILinearContainer.Orientation.Horizontal
            spacing = 8f.dip
            block()
        }
    }


    /**
     * A box that calls [onWave] with a value going back and forth between 0 and 1 every second.
     */
    /**
     * Dispatches a two finger gesture through the host like real touches: finger A presses
     * [target], finger B taps away from it, then finger A is released on [target].
     */
    private fun simulateSecondFingerTap(target: UIComponent) {
        val host = Verktex.host as AndroidGameHost
        val (ax, ay) = target.toSceneSpace(target.bounds.width / 2f, target.bounds.height / 2f)

        // After the current touch, so the gesture does not start inside its dispatch.
        host.window.decorView.post {
            val downTime = SystemClock.uptimeMillis()

            fun send(action: Int, vararg pointers: Triple<Int, Float, Float>) {
                val properties = Array(pointers.size) { i -> MotionEvent.PointerProperties().apply { id = pointers[i].first } }
                val coords = Array(pointers.size) { i -> MotionEvent.PointerCoords().apply { x = pointers[i].second; y = pointers[i].third } }
                val event = MotionEvent.obtain(downTime, SystemClock.uptimeMillis(), action, pointers.size, properties, coords, 0, 0, 1f, 1f, 0, 0, 0, 0)
                host.onTouchEvent(event)
                event.recycle()
            }

            val a = Triple(0, ax, ay)
            val b = Triple(1, ax, ay + 300f)
            val pointer1 = 1 shl MotionEvent.ACTION_POINTER_INDEX_SHIFT

            send(MotionEvent.ACTION_DOWN, a)
            send(MotionEvent.ACTION_POINTER_DOWN or pointer1, a, b)
            send(MotionEvent.ACTION_POINTER_UP or pointer1, a, b)
            send(MotionEvent.ACTION_UP, a)
        }
    }


    companion object {
        /**
         * Background of the boxes the test cases are drawn in.
         */
        private val SURFACE_COLOR = Color4(0xFF313244)
    }


    private class OscillatingBox(color: Color4, private val onWave: UIBox.(Float) -> Unit) : UIBox() {
        private var time = 0f

        init {
            width = 64f.dip
            height = 64f.dip
            radius = 8f.dip
            backgroundColor = color
        }

        override fun onUpdate(clock: TickingClock) {
            time += clock.elapsedTickTime
            onWave((sin(time * PI.toFloat()) + 1f) / 2f)
            super.onUpdate(clock)
        }
    }

    private class SpinningBox(color: Color4, private val degreesPerSecond: Float) : UIBox() {
        init {
            width = 64f.dip
            height = 64f.dip
            radius = 8f.dip
            backgroundColor = color
        }

        override fun onUpdate(clock: TickingClock) {
            rotationZ = (rotationZ + degreesPerSecond * clock.elapsedTickTime) % 360f
            super.onUpdate(clock)
        }
    }
}
