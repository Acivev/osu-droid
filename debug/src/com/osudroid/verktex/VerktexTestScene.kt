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
import com.reco1l.verktex.data.px
import com.reco1l.verktex.graphics.BlendMode
import com.reco1l.verktex.graphics.GraphicsTexture
import com.reco1l.verktex.math.Easing
import com.reco1l.verktex.math.FloatAnimation
import com.reco1l.verktex.math.interpolateFloat
import com.reco1l.verktex.settings.Settings
import com.reco1l.verktex.settings.VerktexSettings
import com.reco1l.verktex.texture.Textures
import com.reco1l.verktex.time.TickingClock
import com.reco1l.verktex.ui.FAIcon
import com.reco1l.verktex.ui.Theme
import com.reco1l.verktex.ui.UIAnimatedSprite
import com.reco1l.verktex.ui.UIBadge
import com.reco1l.verktex.ui.UIButton
import com.reco1l.verktex.ui.UIComponent
import com.reco1l.verktex.ui.UIIcon
import com.reco1l.verktex.ui.UILoadingIndicator
import com.reco1l.verktex.ui.UIScene
import com.reco1l.verktex.ui.UISprite
import com.reco1l.verktex.ui.animatedSprite
import com.reco1l.verktex.ui.badge
import com.reco1l.verktex.ui.card
import com.reco1l.verktex.ui.container.UIContainer
import com.reco1l.verktex.ui.container.UILinearContainer
import com.reco1l.verktex.ui.container.clickableContainer
import com.reco1l.verktex.ui.container.constraintContainer
import com.reco1l.verktex.ui.container.container
import com.reco1l.verktex.ui.container.linearContainer
import com.reco1l.verktex.ui.container.scrollableContainer
import com.reco1l.verktex.ui.control.IntegerTextInput
import com.reco1l.verktex.ui.control.UICheckbox
import com.reco1l.verktex.ui.control.UISelect
import com.reco1l.verktex.ui.control.UITextInput
import com.reco1l.verktex.ui.control.slider
import com.reco1l.verktex.ui.control.textInput
import com.reco1l.verktex.ui.dialog.UIConfirmDialog
import com.reco1l.verktex.ui.dialog.UIMessageDialog
import com.reco1l.verktex.ui.form.FormCheckbox
import com.reco1l.verktex.ui.form.FormContainer
import com.reco1l.verktex.ui.form.FormInput
import com.reco1l.verktex.ui.form.FormSelect
import com.reco1l.verktex.ui.form.FormSlider
import com.reco1l.verktex.ui.form.IntegerFormInput
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
import com.reco1l.verktex.ui.text.TextAlign
import com.reco1l.verktex.ui.text.UITextureText
import com.reco1l.verktex.ui.text.faSolidIcon
import com.reco1l.verktex.ui.text.text
import com.reco1l.verktex.ui.textButton
import kotlin.math.PI
import kotlin.math.min
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
                        textButton {
                            text = "Performance"
                            onActionUp = {
                                Settings[VerktexSettings.ShowPerformanceOverlay] = !Settings[VerktexSettings.ShowPerformanceOverlay, false]
                            }
                        }
                    }
                }

                section("Text input") {
                    val searchLabel = text { text = "Search: (empty)" }
                    +SearchBar().apply {
                        onSearch = { searchLabel.text = if (it.isEmpty()) "Search: (empty)" else "Search: $it" }
                    }

                    val inputLabel = text { text = "Input: (empty)" }
                    textInput {
                        width = 320f.dip
                        placeholder = "Type something..."
                        onValueChange = { inputLabel.text = "Input: $it" }
                        onConfirm = { inputLabel.text = "Confirmed: $value" }
                    }

                    text { text = "Numbers only (0 - 100):" }
                    +IntegerTextInput(null, 0, 100).apply {
                        width = 160f.dip
                        placeholder = "0 - 100"
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

                section("Animated sprite") {
                    val frames = (0..9).mapNotNull { Textures.load("test-digit-$it", "gfx/default-$it.png", isInternal = true) }

                    text { text = "Looping at 4 FPS, and once at 8 FPS:" }

                    lateinit var looping: UIAnimatedSprite
                    lateinit var once: UIAnimatedSprite
                    row {
                        looping = animatedSprite {
                            this.frames = frames
                            frameTime = 1f / 4f
                        }
                        once = animatedSprite {
                            this.frames = frames
                            frameTime = 1f / 8f
                            isLoop = false
                        }
                    }

                    row {
                        textButton {
                            text = "Play"
                            onActionUp = { looping.play(); once.play() }
                        }
                        textButton {
                            text = "Pause"
                            onActionUp = { looping.pause(); once.pause() }
                        }
                        textButton {
                            text = "Stop"
                            onActionUp = { looping.stop(); once.stop() }
                        }
                    }
                }

                section("Texture text") {
                    val digits = ('0'..'9').associateWith { Textures.load("test-score-$it", "gfx/score-$it.png", isInternal = true) }

                    text { text = "Natural widths, and fixed widths sized for \"8888\" aligned to the right:" }

                    val natural = UITextureText(digits).apply {
                        text = "1"
                        spacing = 4f
                    }
                    val fixed = UITextureText(digits).apply {
                        text = "1"
                        spacing = 4f
                        fixedCharWidths = digits['8']?.let { eight -> digits.keys.associateWith { eight.width } }
                        measureText = "8888"
                        textAlign = TextAlign.Right
                        backgroundColor = SURFACE_COLOR
                    }
                    row {
                        +natural
                        +fixed
                    }

                    var score = 1
                    textButton {
                        text = "Add 111"
                        onActionUp = {
                            score = (score + 111) % 10000
                            natural.text = score.toString()
                            fixed.text = score.toString()
                        }
                    }
                }

                section("Health bar") {
                    val background = Textures.load("test-scorebar-bg", "gfx/scorebar-bg.png", isInternal = true)
                    val fill = Textures.load("test-scorebar-colour", "gfx/scorebar-colour.png", isInternal = true)
                    val marker = Textures.load("test-scorebar-marker", "gfx/scorebar-marker.png", isInternal = true)

                    if (background == null || fill == null || marker == null) {
                        text { text = "Failed to load the scorebar textures" }
                        return@section
                    }

                    val hpLabel = text { text = "HP: 1.00" }
                    val bar = HealthBar(background, fill, marker)
                    +bar

                    fun setHp(hp: Float) {
                        bar.hp = hp
                        hpLabel.text = "HP: ${"%.2f".format(bar.hp)}"
                    }

                    row {
                        textButton {
                            text = "Hit"
                            onActionUp = {
                                setHp(bar.hp + 0.05f)
                                bar.flash()
                            }
                        }
                        textButton {
                            text = "Miss"
                            onActionUp = { setHp(bar.hp - 0.15f) }
                        }
                        textButton {
                            text = "Refill"
                            onActionUp = { setHp(1f) }
                        }
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

                section("Slider") {
                    val sliderLabel = text { text = "Slider: 0.5" }
                    slider {
                        width = 400f.dip
                        value = 0.5f
                        precision = 2
                        onValueChange = { sliderLabel.text = "Slider: $it" }
                    }

                    val stepLabel = text { text = "Steps of 10: 50" }
                    slider {
                        width = 400f.dip
                        max = 100f
                        step = 10f
                        value = 50f
                        onValueChange = { stepLabel.text = "Steps of 10: ${it.toInt()}" }
                    }

                    val offsetLabel = text { text = "Offset (-100 to 100): 0" }
                    slider {
                        width = 400f.dip
                        min = -100f
                        max = 100f
                        step = 1f
                        onValueChange = { offsetLabel.text = "Offset (-100 to 100): ${it.toInt()}" }
                    }
                }

                section("Dialogs") {
                    val dialogLabel = text { text = "Dialog result: none" }

                    row {
                        textButton {
                            text = "Message dialog"
                            onActionUp = {
                                UIMessageDialog().apply {
                                    title = "Message"
                                    text = "This is a message dialog. Tap outside of it or the button to close it."
                                    addButton {
                                        text = "Close"
                                        onActionUp = { hide() }
                                    }
                                }.show()
                            }
                        }
                        textButton {
                            text = "Confirm dialog"
                            onActionUp = {
                                UIConfirmDialog().apply {
                                    title = "Confirm"
                                    text = "Do you want to confirm?"
                                    onConfirm = { dialogLabel.text = "Dialog result: confirmed" }
                                    onCancel = { dialogLabel.text = "Dialog result: cancelled" }
                                }.show()
                            }
                        }
                    }
                }

                section("Constraints") {
                    val container = constraintContainer {
                        width = fill()
                        height = 160f.dip
                        backgroundColor = SURFACE_COLOR
                    }

                    // Added before their target, which must still be laid out first.
                    val badge = container.box {
                        width = 16f.dip
                        height = 16f.dip
                        radius = 8f.dip
                        anchor = Anchor.TopRight
                        origin = Anchor.Center
                        backgroundColor = Color4(0xFFF38BA8)
                    }
                    val caption = container.text {
                        text = "Below the target"
                        anchor = Anchor.BottomCenter
                        origin = Anchor.TopCenter
                    }
                    val target = container.box {
                        width = 96f.dip
                        height = 64f.dip
                        y = 24f.dip
                        radius = 8f.dip
                        backgroundColor = Color4(0xFF89B4FA)
                    }

                    container.addConstraint(badge, target)
                    container.addConstraint(caption, target)

                    var moved = false
                    textButton {
                        text = "Move target"
                        onActionUp = {
                            moved = !moved
                            target.x = if (moved) 240f.dip else 0f.dip
                        }
                    }
                }

                section("Forms") {
                    val formLabel = text { text = "Submitted: nothing yet" }

                    val form = FormContainer().apply {
                        orientation = UILinearContainer.Orientation.Vertical
                        width = 500f.dip
                        onSubmit = { formLabel.text = "Submitted: $it" }

                        +FormCheckbox().apply {
                            key = "enabled"
                            label = "Enabled"
                        }
                        +FormSlider(0.5f).apply {
                            key = "volume"
                            label = "Volume"
                            control.precision = 2
                        }
                        +IntegerFormInput(50, 0, 100).apply {
                            key = "offset"
                            label = "Offset (0 - 100)"
                        }
                        +FormSelect<String>().apply {
                            key = "mode"
                            label = "Mode"
                            options = listOf("Easy", "Normal", "Hard").map { UISelect.Option(it, it) }
                        }
                        +FormInput().apply {
                            key = "name"
                            label = "Name"
                            isEnabled = false
                        }
                    }
                    +form

                    textButton {
                        text = "Submit"
                        onActionUp = { form.submit() }
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
     * A search bar like the song select one: collapsed to a square search button while unused, it
     * expands while focused or holding text, and its search icon turns into a clear button with text.
     */
    private class SearchBar : UIContainer() {

        var onSearch: ((String) -> Unit)? = null

        private val input = UITextInput().apply {
            width = fill()
            height = fill()
            placeholder = "Search..."
        }

        private val icon = UIIcon(FAIcon.MagnifyingGlass).apply {
            anchor = Anchor.Center
            origin = Anchor.Center
        }

        private var currentWidth = COLLAPSED_WIDTH.toPixels()

        init {
            height = COLLAPSED_WIDTH
            width = COLLAPSED_WIDTH

            +input
            input.padding = Padding(12f.dip, 0f.dip, COLLAPSED_WIDTH, 0f.dip)
            input.onValueChange = { onSearch?.invoke(it) }

            clickableContainer {
                width = COLLAPSED_WIDTH
                height = fill()
                anchor = Anchor.CenterRight
                origin = Anchor.CenterRight

                +icon

                onActionUp = {
                    if (input.value.isEmpty()) input.focus() else input.value = ""
                }
            }
        }

        override fun onUpdate(clock: TickingClock) {
            val isInUse = input.isFocused || input.value.isNotEmpty()
            val targetWidth = if (isInUse) EXPANDED_WIDTH.toPixels() else COLLAPSED_WIDTH.toPixels()

            currentWidth = clock.interpolateFloat(0.2f, currentWidth, targetWidth)
            width = currentWidth.px

            icon.iconCode = if (input.value.isEmpty()) FAIcon.MagnifyingGlass else FAIcon.Xmark

            super.onUpdate(clock)
        }

        companion object {
            private val COLLAPSED_WIDTH = 40f.dip
            private val EXPANDED_WIDTH = 360f.dip
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

    /**
     * Rebuilds the gameplay health bar (HUDHealthBar) in its new style, with the default skin.
     */
    private class HealthBar(
        background: GraphicsTexture.Region,
        private val fillTexture: GraphicsTexture.Region,
        markerTexture: GraphicsTexture.Region
    ) : UIContainer() {

        var hp = 1f
            set(value) {
                val hp = value.coerceIn(0f, 1f)
                if (hp > field) {
                    bulgeTime = 0f
                }
                field = hp
                displayedHp.target = hp
            }

        private val displayedHp = FloatAnimation(1f, 0.2f, Easing.OutQuint)

        private var bulgeTime = BULGE_DURATION
        private var explodeTime = EXPLODE_DURATION
        private var explodeScale = 1f

        private val fill = UIAnimatedSprite(listOf(fillTexture))

        // The fill is cut at the current HP, as the depth buffer clearing box does in gameplay.
        private val fillClip = UIContainer().apply {
            x = FILL_X.px
            y = FILL_Y.px
            height = fillTexture.height.px
            clipping = ClipMode.Bounds
            +fill
        }

        private val marker = UISprite(markerTexture).apply { origin = Anchor.Center }

        private val explode = UISprite(markerTexture).apply {
            origin = Anchor.Center
            alpha = 0f
        }


        init {
            +UISprite(background)
            +fillClip
            +marker
            +explode
        }


        /**
         * Flashes the marker like a hit note does.
         */
        fun flash() {
            bulgeTime = 0f
            explodeTime = 0f
            explodeScale = if (hp >= EPIC_CUTOFF) 2f else 1.6f
            explode.blendMode = if (hp >= EPIC_CUTOFF) BlendMode.Additive else BlendMode.Alpha
        }

        override fun onUpdate(clock: TickingClock) {
            val fillWidth = fillTexture.width * displayedHp.update(clock)
            fillClip.width = fillWidth.px

            val markerX = (FILL_X + fillWidth).px
            val markerY = (FILL_Y + fillTexture.height / 2f).px
            marker.x = markerX
            marker.y = markerY
            explode.x = markerX
            explode.y = markerY

            bulgeTime += clock.elapsedTickTime
            marker.scale = 1f + 0.4f * (1f - Easing.OutQuad.interpolate(min(bulgeTime / BULGE_DURATION, 1f)))

            explodeTime += clock.elapsedTickTime
            val explodeProgress = min(explodeTime / EXPLODE_DURATION, 1f)
            explode.scale = 1f + (explodeScale - 1f) * explodeProgress
            explode.alpha = 1f - explodeProgress

            val color = fillColor(hp)
            fill.color = color
            marker.color = color
            // The marker glows while the HP is high, as in gameplay.
            marker.blendMode = if (hp < EPIC_CUTOFF) BlendMode.Alpha else BlendMode.Additive

            super.onUpdate(clock)
        }

        private fun fillColor(hp: Float) = when {
            hp < 0.2f -> lerp(Color4.Black, RED, (0.2f - hp) / 0.2f)
            hp < EPIC_CUTOFF -> lerp(Color4.White, Color4.Black, (EPIC_CUTOFF - hp) / EPIC_CUTOFF)
            else -> Color4.White
        }

        private fun lerp(from: Color4, to: Color4, t: Float) = Color4(
            from.red + (to.red - from.red) * t,
            from.green + (to.green - from.green) * t,
            from.blue + (to.blue - from.blue) * t
        )

        companion object {
            // Offsets of the fill in the new style, see HUDHealthBar.
            private const val FILL_X = 7.5f * 1.6f
            private const val FILL_Y = 7.8f * 1.6f

            private const val EPIC_CUTOFF = 0.5f
            private const val BULGE_DURATION = 0.2f
            private const val EXPLODE_DURATION = 0.12f

            private val RED = Color4(1f, 0f, 0f)
        }
    }
}
