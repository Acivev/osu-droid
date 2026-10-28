package com.osudroid.ui.v2

import android.text.InputType
import androidx.annotation.IdRes
import androidx.annotation.VisibleForTesting
import com.edlplan.ui.ActivityOverlay
import com.edlplan.framework.easing.Easing
import com.osudroid.ui.v1.SettingsFragment
import com.osudroid.ui.v2.modmenu.LegacyButton
import com.osudroid.ui.v2.modmenu.LegacyTextButton
import com.osudroid.ui.verktex.FontAwesomeIcon
import com.osudroid.ui.verktex.LegacyUnits
import com.osudroid.ui.verktex.LegacyUnits.vu
import com.osudroid.ui.verktex.OutlineCircle
import com.osudroid.ui.verktex.SkinTextures
import com.osudroid.ui.verktex.setText
import com.osudroid.ui.verktex.times
import com.osudroid.utils.mainThread
import com.osudroid.utils.updateThread
import com.osudroid.resources.R.string
import com.osudroid.verktex.bridge.EngineRouter
import com.reco1l.verktex.Verktex
import com.reco1l.verktex.data.Anchor
import com.reco1l.verktex.data.Axis
import com.reco1l.verktex.data.ClipMode
import com.reco1l.verktex.data.Color4
import com.reco1l.verktex.data.LayoutConstraints
import com.reco1l.verktex.data.Padding
import com.reco1l.verktex.data.Vec2
import com.reco1l.verktex.data.Vec4
import com.reco1l.verktex.data.fill
import com.reco1l.verktex.data.px
import com.reco1l.verktex.graphics.GraphicsRenderer
import com.reco1l.verktex.input.InputEvent
import com.reco1l.verktex.input.KeyboardEvent
import com.reco1l.verktex.input.PointerEvent
import com.reco1l.verktex.input.ScrollEvent
import com.reco1l.verktex.time.TickingClock
import com.reco1l.verktex.ui.FAIcon
import com.reco1l.verktex.ui.UIScene
import com.reco1l.verktex.ui.UISprite
import com.reco1l.verktex.ui.container.UIContainer
import com.reco1l.verktex.ui.container.UILinearContainer
import com.reco1l.verktex.ui.container.UILinearContainer.Orientation
import com.reco1l.verktex.ui.control.UIControl
import com.reco1l.verktex.ui.shape.UIBox
import com.reco1l.verktex.ui.shape.UICircle
import com.reco1l.verktex.ui.shape.box
import com.reco1l.verktex.ui.shape.circle
import com.reco1l.verktex.ui.text.UIText
import com.reco1l.verktex.ui.text.text
import com.reco1l.osu.ui.PromptDialog
import com.osudroid.beatmaps.DroidHitWindow
import com.osudroid.math.Interpolation
import com.osudroid.utils.median
import com.osudroid.utils.standardDeviation
import com.rian.andengine.modifier.ModifierType
import com.rian.andengine.modifier.clearModifiers
import com.rian.andengine.modifier.fadeIn
import com.rian.andengine.modifier.fadeOut
import com.rian.andengine.modifier.fadeTo
import com.rian.andengine.modifier.scaleTo
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.roundToInt
import ru.nsu.ccfit.zuev.audio.Status
import ru.nsu.ccfit.zuev.osu.Config
import ru.nsu.ccfit.zuev.osu.GlobalManager
import ru.nsu.ccfit.zuev.osu.ResourceManager
import ru.nsu.ccfit.zuev.osu.helper.StringTable

object CalibrationScene : UIScene() {
    internal var OFFSET_MIN = -500
    internal var OFFSET_MAX = 500

    private const val APPROACH_SCALE_START = 3f
    private const val CIRCLE_DIAMETER = 130f
    private const val STEP_MS = 1
    private const val STEP_MS_LONG = 10
    private const val STEP_BPM = 1
    private const val STEP_BPM_LONG = 10
    private const val MAX_TAP_SAMPLES = 20

    private var currentBpm = 60

    private val beatInterval
        get() = 60f / currentBpm

    private var beatTimer = 0f
    @VisibleForTesting
    internal var metronomeTime = 0.0
    @VisibleForTesting
    internal var pendingOffset = 0

    private val judgementHitWindow = DroidHitWindow(10f)
    @VisibleForTesting
    internal val tapOffsets = mutableListOf<Double>()
    private var wasMusicPlaying = false
    @VisibleForTesting
    internal var successfulTapStreak = 0

    /**
     * Invoked on the main thread after the overlay closes (Back or SET).
     * Set by the caller so that the current settings can re-show itself (e.g. Settings → Audio).
     */
    internal var settingsFragment: SettingsFragment? = null

    /**
     * Posts to the main thread. Replaceable for tests.
     */
    @VisibleForTesting
    internal var mainThreadPost: (() -> Unit) -> Unit = { block -> mainThread { block() } }

    /**
     * Whether an activity overlay (e.g. the BPM or offset input dialog) is displayed. Replaceable for tests.
     */
    @VisibleForTesting
    internal var isActivityOverlayDisplayed: () -> Boolean = { ActivityOverlay.isDisplayingOverlay }

    private lateinit var approachCircle: LegacyOutlineCircle
    private lateinit var hitCircleFill: UICircle
    private lateinit var rippleCircle: LegacyOutlineCircle
    private var circleContainer: UIContainer
    private lateinit var bpmValueText: LegacyTextButton
    private lateinit var offsetValueText: LegacyTextButton
    private lateinit var tapFeedbackText: UIText
    private lateinit var judgementText: UIText
    private lateinit var streakText: UIText
    private lateinit var highPrecisionToggle: LegacyCheckbox

    init {
        // Background
        box {
            width = fill()
            height = fill()
            style = Style { theme -> backgroundColor = theme.palette.accent * 0.08f }
        }

        val tapAreaSize = CIRCLE_DIAMETER * APPROACH_SCALE_START

        +LegacyTextButton("Back", "back-arrow", "back-arrow.png", SkinTextures::getAsset).apply {
            anchor = Anchor.TopLeft
            origin = Anchor.TopLeft
            translationX = 60f.vu.px
            translationY = 12f.vu.px
            onActionUp = {
                playShortClickConfirmSound()
                back()
            }
            onActionCancel = { playShortClickSound() }
        }

        circleContainer = object : UIContainer() {
            override fun onInputEvent(event: InputEvent): Boolean {
                if (super.onInputEvent(event)) {
                    return true
                }

                // The AndEngine container took the whole touch, only its press counted as a tap.
                if (event is PointerEvent && event.action == PointerEvent.Action.Down) {
                    val (localX, localY) = toLocalSpaceFromScene(event.x, event.y)
                    val cx = tapAreaSize.vu / 2f
                    val cy = tapAreaSize.vu / 2f
                    val dx = localX - cx
                    val dy = localY - cy

                    if (dx * dx + dy * dy <= cx * cx) {
                        onPlayfieldTap()
                    }

                    return true
                }

                return false
            }
        }.apply {
            width = CIRCLE_DIAMETER.vu.px
            height = CIRCLE_DIAMETER.vu.px
            anchor = Anchor.Center
            origin = Anchor.Center

            circle {
                width = fill()
                height = fill()
                anchor = Anchor.Center
                origin = Anchor.Center
                style = Style { theme -> color = theme.palette.accent * 0.35f }
                hitCircleFill = this
            }

            +LegacyOutlineCircle().apply {
                width = fill()
                height = fill()
                anchor = Anchor.Center
                origin = Anchor.Center
                legacyLineWidth = 8f
                style = Style { theme -> color = theme.palette.accent }
            }

            +LegacyOutlineCircle().apply {
                width = fill()
                height = fill()
                anchor = Anchor.Center
                origin = Anchor.Center
                legacyLineWidth = 6f
                scaleOrigin = Anchor.Center
                scale = APPROACH_SCALE_START
                style = Style { theme -> color = theme.palette.accent * 0.85f }
                approachCircle = this
            }

            +LegacyOutlineCircle().apply {
                width = fill()
                height = fill()
                anchor = Anchor.Center
                origin = Anchor.Center
                legacyLineWidth = 6f
                scaleOrigin = Anchor.Center
                alpha = 0f
                style = Style { theme -> color = theme.palette.accent }
                rippleCircle = this
            }
        }

        +circleContainer

        text {
            applyLegacyText()
            alignment = Anchor.Center
            anchor = Anchor.Center
            origin = Anchor.BottomCenter
            translationY = (-(CIRCLE_DIAMETER / 2 + 20f)).vu.px
            scaleOrigin = Anchor.Center
            alpha = 0f
            judgementText = this
        }

        text {
            applyLegacyText()
            alignment = Anchor.Center
            anchor = Anchor.Center
            origin = Anchor.TopCenter
            translationY = (CIRCLE_DIAMETER / 2 + 24f).vu.px
            tapFeedbackText = this
        }

        text {
            applyLegacyText()
            alignment = Anchor.Center
            anchor = Anchor.Center
            origin = Anchor.TopCenter
            translationY = (CIRCLE_DIAMETER / 2 + 56f).vu.px
            scaleOrigin = Anchor.Center
            streakText = this
        }

        text {
            setText(string.opt_offset_calibration_tap_hint)
            applyLegacyText()
            alignment = Anchor.BottomCenter
            anchor = Anchor.BottomCenter
            origin = Anchor.BottomCenter
            translationY = (-28f).vu.px
            style = Style { theme -> color = theme.palette.accent * 0.45f }
        }

        flexContainer {
            width = 300f.vu.px
            anchor = Anchor.CenterRight
            origin = Anchor.CenterRight
            translationX = (-20f).vu.px
            spacing = 20f.vu.px
            padding = Padding(24f.vu.px)

            radius = 16f.vu.px
            style = Style { theme -> backgroundColor = theme.palette.accent * 0.15f }

            text {
                setText(string.opt_offset_calibration_calibration)
                applyLegacyText()
                alignment = Anchor.TopCenter
                anchor = Anchor.TopCenter
                origin = Anchor.TopCenter
                style = Style { theme -> color = theme.palette.accent }
            }

            box {
                width = fill()
                height = 1f.vu.px
                style = Style { theme -> backgroundColor = theme.palette.accent * 0.2f }
            }

            text {
                text = "BPM"
                applyLegacyText()
                alignment = Anchor.TopCenter
                anchor = Anchor.TopCenter
                origin = Anchor.TopCenter
                style = Style { theme -> color = theme.palette.accent * 0.6f }
            }

            linearContainer {
                orientation = Orientation.Horizontal
                anchor = Anchor.TopCenter
                origin = Anchor.TopCenter
                spacing = 12f.vu.px

                +CaretButton(FAIcon.CaretDown).apply {
                    onActionUp = { changeBpm(-STEP_BPM) }
                    onActionLongPress = { changeBpm(-STEP_BPM_LONG) }
                }

                +ValueButton().apply {
                    text = currentBpm.toString()
                    onActionUp = { showBpmInputDialog() }
                    bpmValueText = this
                }

                +CaretButton(FAIcon.CaretUp).apply {
                    onActionUp = { changeBpm(STEP_BPM) }
                    onActionLongPress = { changeBpm(STEP_BPM_LONG) }
                }
            }

            box {
                width = fill()
                height = 1f.vu.px
                style = Style { theme -> backgroundColor = theme.palette.accent * 0.2f }
            }

            text {
                setText(string.opt_offset_calibration_offset)
                applyLegacyText()
                alignment = Anchor.TopCenter
                anchor = Anchor.TopCenter
                origin = Anchor.TopCenter
                style = Style { theme -> color = theme.palette.accent * 0.6f }
            }

            linearContainer {
                orientation = Orientation.Horizontal
                anchor = Anchor.TopCenter
                origin = Anchor.TopCenter
                spacing = 12f.vu.px

                +CaretButton(FAIcon.CaretDown).apply {
                    onActionUp = { changeOffset(-STEP_MS) }
                    onActionLongPress = { changeOffset(-STEP_MS_LONG) }
                }

                +ValueButton().apply {
                    text = formatOffset(pendingOffset)
                    onActionUp = { showOffsetInputDialog() }
                    offsetValueText = this
                }

                +CaretButton(FAIcon.CaretUp).apply {
                    onActionUp = { changeOffset(STEP_MS) }
                    onActionLongPress = { changeOffset(STEP_MS_LONG) }
                }
            }

            +LegacyTextButton("", null, null).apply {
                setText(string.opt_offset_calibration_set)
                anchor = Anchor.TopCenter
                origin = Anchor.TopCenter
                onActionUp = {
                    playShortClickConfirmSound()
                    applyOffset()
                    back()
                }
                onActionCancel = { playShortClickSound() }
            }

            +ResetButton().apply {
                setText(string.opt_offset_calibration_reset)
                anchor = Anchor.TopCenter
                origin = Anchor.TopCenter
                style = Style {}
                backgroundColor = Color4(0xFF342121)
                onActionUp = {
                    playShortClickSound()
                    pendingOffset = 0
                    successfulTapStreak = 0
                    tapOffsets.clear()
                    updateOffsetDisplay()
                    updateTapFeedback()
                    updateStreakDisplay()
                }
                onActionCancel = { playShortClickSound() }
            }

        }

        flexContainer {
            width = 400f.vu.px
            anchor = Anchor.BottomLeft
            origin = Anchor.BottomLeft
            translationX = 20f.vu.px
            translationY = (-20f).vu.px
            spacing = 8f.vu.px
            padding = Padding(14f.vu.px)

            radius = 16f.vu.px
            style = Style { theme -> backgroundColor = theme.palette.accent * 0.15f }

            // Header
            text {
                setText(string.opt_offset_calibration_settings)
                applyLegacyText()
                alignment = Anchor.TopCenter
                anchor = Anchor.TopCenter
                origin = Anchor.TopCenter
                style = Style { theme -> color = theme.palette.accent }
            }

            // Divider
            box {
                width = fill()
                height = 1f.vu.px
                style = Style { theme -> backgroundColor = theme.palette.accent * 0.2f }
            }

            // Setting row: label (left) + ON/OFF toggle (right)
            linearContainer {
                orientation = Orientation.Horizontal
                anchor = Anchor.TopLeft
                origin = Anchor.TopLeft
                spacing = 8f.vu.px
                width = fill()

                text {
                    setText(string.opt_highPrecisionInput_title)
                    applyLegacyText()
                    alignment = Anchor.CenterLeft
                    anchor = Anchor.CenterLeft
                    origin = Anchor.CenterLeft
                    style = Style { theme -> color = theme.palette.accent * 0.9f }
                }

                highPrecisionToggle = object : LegacyCheckbox(Config.isHighPrecisionInput()) {
                    override fun onValueChanged() {
                        super.onValueChanged()

                        Config.setBoolean("highPrecisionInput", value)
                    }
                }
                +highPrecisionToggle
            }

            // Description below the row
            text {
                setText(string.opt_highPrecisionInput_summary)
                applyLegacyText()
                width = fill()
                clipping = ClipMode.Bounds
                alignment = Anchor.TopLeft
                anchor = Anchor.TopLeft
                origin = Anchor.TopLeft
                style = Style { theme -> color = theme.palette.accent * 0.5f }
            }
        }
    }

    fun show() {
        pendingOffset = Config.getOffset().toInt()
        beatTimer = 0f
        metronomeTime = 0.0
        successfulTapStreak = 0
        tapOffsets.clear()
        updateOffsetDisplay()
        updateTapFeedback()
        highPrecisionToggle.value = Config.isHighPrecisionInput()

        val songService = GlobalManager.getInstance().songService
        wasMusicPlaying = songService.status == Status.PLAYING

        if (wasMusicPlaying) {
            songService.pause()
        }

        // The router keeps the FPS counter and the room chat above it, as the AndEngine overlay did.
        EngineRouter.showOverlay(this)
    }

    fun back() {
        if (wasMusicPlaying) {
            GlobalManager.getInstance().songService.play()
        }

        mainThreadPost {
            settingsFragment?.show()
            settingsFragment = null
        }

        EngineRouter.hideOverlay(this)
    }

    override fun onInputEvent(event: InputEvent): Boolean {
        if (event is KeyboardEvent) {
            if (event.key == KeyboardEvent.Key.Back && event.action == KeyboardEvent.Action.Down) {
                // A displayed dialog takes the key, AndEngine asked ActivityOverlay.onBackPress() first.
                // The key is consumed here, the scene below would take it otherwise.
                if (isActivityOverlayDisplayed()) {
                    mainThreadPost { ActivityOverlay.onBackPress() }
                    return true
                }

                back()
                return true
            }
            return super.onInputEvent(event)
        }

        if (super.onInputEvent(event)) {
            return true
        }

        // The AndEngine child scene was modal for touches. Cancel events are dispatched to every
        // component, so they are not consumed.
        return event is ScrollEvent || event is PointerEvent && event.action != PointerEvent.Action.Unknown && event.action != PointerEvent.Action.Cancel
    }

    // The overlay container keeps the safe area free and UIScene pads its content by it, the AndEngine
    // child scene covered the whole window. The children are measured and laid out over the window
    // instead, which relies on the overlay container sitting at the window origin.

    override fun onMeasure(parentConstraints: LayoutConstraints, contentConstraints: LayoutConstraints): Vec2 {
        val windowSize = windowSize()
        val childConstraints = LayoutConstraints(0f, windowSize.x, 0f, windowSize.y)

        forEach { it.measure(childConstraints) }

        return windowSize
    }

    override fun layout(assignedBounds: Vec4) {
        val windowSize = windowSize()

        measuredSize = windowSize
        super.layout(Vec4(0f, 0f, windowSize.x, windowSize.y))
    }

    override fun onLayout(bounds: Vec4) {
        val window = Vec4(0f, 0f, bounds.width, bounds.height)
        forEach { it.layout(window) }
    }

    private fun windowSize() = Vec2(
        Verktex.graphics.window.windowWidth.toFloat(),
        Verktex.graphics.window.windowHeight.toFloat()
    )

    override fun onUpdate(clock: TickingClock) {
        super.onUpdate(clock)

        val deltaTimeSec = clock.elapsedTickTime

        metronomeTime += deltaTimeSec.toDouble()
        beatTimer += deltaTimeSec

        val progress = (beatTimer / beatInterval).coerceIn(0f, 1f)
        approachCircle.scale = Interpolation.linear(APPROACH_SCALE_START, 1f, progress)

        while (beatTimer >= beatInterval) {
            beatTimer -= beatInterval
            onBeat()
        }
    }

    // -------------------------------------------------------------------------
    // Beat / animation
    // -------------------------------------------------------------------------

    private fun onBeat() {
        ResourceManager.getInstance().getSound("drum-hitclap")?.play()

        hitCircleFill.apply {
            clearModifiers(ModifierType.Alpha)
            alpha = 0.7f
            fadeTo(0.35f, beatInterval * 0.5f, Easing.Out)
        }
    }

    // -------------------------------------------------------------------------
    // Tap calibration
    // -------------------------------------------------------------------------

    @VisibleForTesting
    internal fun onPlayfieldTap() {
        val beatMs = beatInterval * 1000
        val currentMs = metronomeTime * 1000
        val phase = currentMs % beatMs
        val error = if (phase <= beatMs / 2) phase else phase - beatMs
        val absErr = abs(error).roundToInt()

        tapOffsets.add(error)

        if (tapOffsets.size > MAX_TAP_SAMPLES) {
            tapOffsets.removeAt(0)
        }

        pendingOffset = computePendingOffset()

        val judgement = when {
            absErr < judgementHitWindow.greatWindow -> Judgement.PERFECT
            absErr < judgementHitWindow.okWindow -> Judgement.GOOD
            absErr < judgementHitWindow.mehWindow -> Judgement.MEH
            else -> Judgement.MISS
        }

        when (judgement) {
            Judgement.PERFECT, Judgement.GOOD -> {
                successfulTapStreak++
                playShortClickConfirmSound()
            }

            else -> {
                successfulTapStreak = 0
                playShortClickSound()
            }
        }

        updateOffsetDisplay()
        updateTapFeedback(error)
        updateStreakDisplay(judgement)

        showJudgement(judgement)
        triggerRipple(judgement)
        punchCircle()
    }

    private enum class Judgement(@field:IdRes val label: Int, val color: Color4) {
        PERFECT(string.opt_offset_calibration_judgement_perfect, Color4(0xFF6ECFFF)),
        GOOD(string.opt_offset_calibration_judgement_good, Color4(0xFF64DC28)),
        MEH(string.opt_offset_calibration_judgement_meh, Color4(0xFFc8b46e)),
        MISS(string.opt_offset_calibration_judgement_miss, Color4(0xFFFF4444))
    }

    /** Pop-in judgement badge above the hit circle: scale 0.7 → 1.1 → fade out. */
    private fun showJudgement(judgement: Judgement) {
        judgementText.apply {
            clearModifiers(false, ModifierType.Alpha, ModifierType.ScaleXY)
            setText(judgement.label)
            color = judgement.color
            scale = 0.7f
            alpha = 1f

            scaleTo(1.1f, 0.1f, Easing.Out).after {
                scaleTo(1f, 0.05f)
                fadeOut(0.45f, Easing.In)
            }
        }
    }

    /** Expanding outline ring that bursts outward and fades – colored by judgement. */
    private fun triggerRipple(judgement: Judgement) {
        rippleCircle.apply {
            clearModifiers(false, ModifierType.Alpha, ModifierType.ScaleXY)
            color = judgement.color
            scale = 1f
            alpha = 0.75f

            scaleTo(1.5f, 0.2f)
            fadeOut(0.2f)
        }
    }

    /** Brief scale-up of the whole circle group – the classic osu! hit punch. */
    private fun punchCircle() {
        circleContainer.apply {
            clearModifiers(ModifierType.ScaleXY)
            scale = 1f

            scaleTo(1.12f, 0.08f, Easing.Out).after {
                scaleTo(1f, 0.12f, Easing.In)
            }
        }
    }


    private fun updateStreakDisplay(judgement: Judgement? = null) {
        streakText.apply {
            when {
                successfulTapStreak >= 10 -> {
                    text = StringTable.format(string.opt_offset_calibration_streak, successfulTapStreak)
                    color = Color4(0xFFFFAA00)

                    // tiny pulse on milestone
                    if (judgement != null && successfulTapStreak % 10 == 0) {
                        clearModifiers(ModifierType.ScaleXY)
                        scaleTo(1.3f, 0.1f, Easing.Out).after {
                            scaleTo(1f, 0.1f, Easing.In)
                        }
                    }
                }

                successfulTapStreak >= 3 -> {
                    text = StringTable.format(string.opt_offset_calibration_streak_combo, successfulTapStreak)
                    color = Color4(0xFF88FF88)
                }

                else -> text = ""
            }
        }
    }

    private fun updateTapFeedback(lastError: Double? = null) {
        if (lastError != null) {
            val absErr = abs(lastError).roundToInt()

            val label = StringTable.get(when {
                absErr < judgementHitWindow.greatWindow -> string.opt_offset_calibration_feedback_perfect
                lastError > 0 -> string.opt_offset_calibration_feedback_late
                else -> string.opt_offset_calibration_feedback_early
            })

            val detail =
                if (absErr < 16) label
                else "$label (${if (lastError > 0) "+" else ""}${lastError.roundToInt()} ms)"

            tapFeedbackText.text = "$detail  •  ${tapOffsets.size} ${StringTable.get(string.opt_offset_calibration_feedback_tap_count)}"
            tapFeedbackText.color = when {
                absErr < judgementHitWindow.greatWindow -> Color4(0xFF88FF88)
                absErr < judgementHitWindow.okWindow -> Color4(0xFFFFFF88)
                else -> Color4(0xFFFF8888)
            }
        } else {
            tapFeedbackText.text = if (tapOffsets.isNotEmpty()) "${tapOffsets.size} ${StringTable.get(string.opt_offset_calibration_feedback_tap_count)}" else ""
            tapFeedbackText.color = Color4(0xFFAAAAAA)
        }
    }

    // -------------------------------------------------------------------------
    // Offset / BPM helpers
    // -------------------------------------------------------------------------

    private fun changeOffset(delta: Int) {
        tapOffsets.clear()
        pendingOffset = (pendingOffset + delta).coerceIn(OFFSET_MIN, OFFSET_MAX)
        updateOffsetDisplay()
        updateTapFeedback()
        playShortClickSound()
    }

    private fun changeBpm(delta: Int) {
        currentBpm = (currentBpm + delta).coerceIn(30, 300)
        beatTimer = 0f
        metronomeTime = 0.0
        updateBpmDisplay()
        playShortClickSound()
    }

    private fun showOffsetInputDialog() {
        mainThread {
            PromptDialog().apply {
                setTitle(StringTable.get(com.osudroid.resources.R.string.opt_offset_title))
                setMessage(StringTable.get(com.osudroid.resources.R.string.opt_offset_summary))
                setInput(pendingOffset.toString())
                setInputType(InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_SIGNED)

                addButton("OK") { dialog ->
                    dialog as PromptDialog

                    val newValue = dialog.input?.toIntOrNull() ?: pendingOffset
                    pendingOffset = newValue.coerceIn(OFFSET_MIN, OFFSET_MAX)

                    updateThread {
                        Verktex.withTreeLock {
                            tapOffsets.clear()
                            updateOffsetDisplay()
                            updateTapFeedback()
                        }
                    }

                    dismiss()
                }

                addButton("Cancel") { dialog ->
                    dialog.dismiss()
                }

                show()
            }
        }
    }

    private fun showBpmInputDialog() {
        mainThread {
            PromptDialog().apply {
                setTitle("BPM")
                setInput(currentBpm.toString())
                setInputType(InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_SIGNED)

                addButton("OK") { dialog ->
                    dialog as PromptDialog

                    val newValue = dialog.input?.toIntOrNull() ?: currentBpm

                    updateThread {
                        Verktex.withTreeLock {
                            changeBpm(newValue - currentBpm)
                        }
                    }

                    dismiss()
                }

                addButton("Cancel") { dialog ->
                    dialog.dismiss()
                }

                show()
            }
        }
    }

    private fun updateOffsetDisplay() {
        offsetValueText.text = formatOffset(pendingOffset)
    }

    private fun updateBpmDisplay() {
        bpmValueText.text = currentBpm.toString()
    }

    private fun formatOffset(ms: Int) = when {
        ms > 0 -> "+${ms} ms"
        ms < 0 -> "$ms ms"
        else -> "0 ms"
    }

    private fun playShortClickSound() {
        ResourceManager.getInstance().getSound("click-short")?.play()
    }

    private fun playShortClickConfirmSound() {
        ResourceManager.getInstance().getSound("click-short-confirm")?.play()
    }

    private fun applyOffset() {
        Config.setOffset(pendingOffset.toFloat())
        Config.setInt("offset", pendingOffset)
    }

    private fun computePendingOffset(): Int {
        if (tapOffsets.isEmpty()) {
            return 0
        }

        val unstableRate = tapOffsets.standardDeviation()
        var offset = tapOffsets.median()

        if (unstableRate >= 90) {
            // A demonstrative graph of this algorithm is embedded in https://github.com/ppy/osu/discussions/30521.
            // This prevents high unstable rate from suggesting potentially invalid offsets.
            offset *= exp(-0.0116 * (unstableRate - 90))
        }

        return offset.roundToInt().coerceIn(OFFSET_MIN, OFFSET_MAX)
    }
}


//region Verktex twins of the AndEngine components

/**
 * The defaults of the AndEngine UIText in smallFont: no wrapping and a horizontal auto scroll of
 * overflowing text at 15 virtual units per second.
 */
private fun UIText.applyLegacyText() {
    fontSize = LegacyUnits.fontSize("smallFont").vu.px
    wrapText = false
    autoScrollAxes = Axis.X
    autoScrollSpeed = 15f.vu
}

/**
 * The AndEngine UIFlexContainer column: the children follow each other with [UILinearContainer.spacing]
 * as the gap. Its content is never larger than the children, so the centred justification of the
 * calibration panels had no effect.
 */
private fun UIContainer.flexContainer(builder: AnchoredLinearContainer.() -> Unit) =
    AnchoredLinearContainer().apply { orientation = Orientation.Vertical }.apply(builder).also { +it }

/**
 * The AndEngine UILinearContainer, see [AnchoredLinearContainer].
 */
private fun UIContainer.linearContainer(builder: AnchoredLinearContainer.() -> Unit) =
    AnchoredLinearContainer().apply(builder).also { +it }

/**
 * The AndEngine linear and flex containers only placed their children along the main axis, on the cross
 * axis a child kept its anchor and origin (e.g. a centred title, a label centred next to a checkbox).
 * The Verktex linear container lays every child out at the start of the cross axis, so here the cross
 * axis gets the whole content size and the anchor and origin position the child in it.
 */
private class AnchoredLinearContainer : UILinearContainer() {

    override fun onLayout(bounds: Vec4) {
        val content = contentBounds(bounds)
        var current = if (orientation == Orientation.Horizontal) content.x else content.y

        forEach { child ->
            val size = child.measuredSize

            if (orientation == Orientation.Horizontal) {
                child.layout(Vec4(current, content.y, current + size.x, content.w))
                current += size.x + spacing.toPixels()
            } else {
                child.layout(Vec4(content.x, current, content.z, current + size.y))
                current += size.y + spacing.toPixels()
            }
        }
    }
}

/**
 * The AndEngine outline UICircle. glLineWidth ignored the entity's scale, so the stroke keeps its
 * window-pixel width while the circle and its parents are scaled.
 */
private class LegacyOutlineCircle : OutlineCircle() {

    /**
     * The width of the line in window pixels, at any scale.
     */
    var legacyLineWidth = 1f

    override fun onDraw(renderer: GraphicsRenderer) {
        var scale = scaleX
        var parent = parent
        while (parent != null) {
            scale *= parent.scaleX
            parent = parent.parent
        }

        lineWidth = if (scale > 0f) legacyLineWidth / scale else legacyLineWidth
        super.onDraw(renderer)
    }
}

/**
 * The AndEngine UITextButton showing the BPM or the offset: the theme colour on an accent * 0.12
 * background with a corner radius of 8, at least 110 virtual units wide, the text centred in it.
 */
private class ValueButton : LegacyTextButton("", null, null) {

    init {
        style = style + Style { theme -> backgroundColor = theme.palette.accent * 0.12f }
    }

    override fun onApplyLegacySizes() {
        super.onApplyLegacySizes()
        radius = 8f.vu.px
    }

    override fun onMeasure(parentConstraints: LayoutConstraints, contentConstraints: LayoutConstraints): Vec2 {
        val size = super.onMeasure(parentConstraints, contentConstraints)
        return Vec2(max(size.x, 110f.vu), size.y)
    }
}

/**
 * The "Reset" button, coloured by itself instead of the theme.
 */
private class ResetButton : LegacyTextButton("", null, null) {
    override fun applyTint(tint: Color4) = super.applyTint(Color4(0xFFFFBFBF))
}

/**
 * The AndEngine UITextButton with a FontAwesome leading icon and no text: the icon is as high as the
 * (empty) smallFont text and 8 apart from it. The icon was coloured by the theme accent and drawn
 * multiplied by the button colour.
 */
private class CaretButton(iconCode: Int) : LegacyButton() {

    private val icon = FontAwesomeIcon(iconCode)

    private val label = UIText().apply {
        text = ""
        wrapText = false
    }

    private val row = UILinearContainer().apply {
        orientation = Orientation.Horizontal
        anchor = Anchor.Center
        origin = Anchor.Center

        +icon
        +label
    }

    // The label height the icon was last sized to.
    private var iconSize = 0f

    init {
        +row
    }

    override fun onApplyLegacySizes() {
        row.spacing = 8f.vu.px
        label.fontSize = LegacyUnits.fontSize("smallFont").vu.px
    }

    override fun applyTint(tint: Color4) {
        icon.color = Verktex.theme.palette.accent * tint
        label.color = tint
    }

    override fun onUpdate(clock: TickingClock) {
        // CompoundText sized its leading icon to the text height on both axes.
        if (iconSize != label.measuredSize.y) {
            iconSize = label.measuredSize.y
            icon.boxSize = iconSize / LegacyUnits.scale
        }

        super.onUpdate(clock)
    }
}

/**
 * The AndEngine UICheckbox: 48 virtual units with a corner radius of 12, an accent * 0.5 background
 * when checked and accent * 0.25 otherwise, faded in 0.1 seconds, and the 32 units "check" sprite
 * scaled in and out with a bounce. A release of the finger that pressed it toggles it, wherever it is.
 */
private open class LegacyCheckbox(initialValue: Boolean) : UIControl<Boolean>(initialValue) {

    private val background = UIBox().apply {
        width = fill()
        height = fill()
        radius = 12f.vu.px
        style = Style { theme ->
            backgroundColor = theme.palette.accent * if (value) 0.5f else 0.25f
            backgroundElapsed = BACKGROUND_FADE_DURATION
        }
    }

    // A UIBox draws its background colour, which the modifiers do not animate; colorTo() of the
    // AndEngine background is faded here, linearly in 0.1 seconds.
    private var backgroundStart = Color4.White
    private var backgroundTarget = Color4.White
    private var backgroundElapsed = BACKGROUND_FADE_DURATION

    private val checkSprite = UISprite().apply {
        width = 32f.vu.px
        height = 32f.vu.px
        anchor = Anchor.Center
        origin = Anchor.Center
        style = Style { theme -> color = theme.palette.accent }

        if (!initialValue) {
            scale = 0f
            alpha = 0f
        }
    }

    private var shouldLoadTexture = true

    private var isPressed = false
    private var pressPointerId = -1

    init {
        width = 48f.vu.px
        height = 48f.vu.px

        +background
        +checkSprite
    }

    override fun onValueChanged() {
        super.onValueChanged()

        checkSprite.clearModifiers(false, ModifierType.Alpha, ModifierType.ScaleXY)

        backgroundStart = background.backgroundColor
        backgroundElapsed = 0f

        if (value) {
            backgroundTarget = Verktex.theme.palette.accent * 0.5f
            checkSprite.scaleTo(1f, 0.2f, Easing.OutBounce)
        } else {
            backgroundTarget = Verktex.theme.palette.accent * 0.25f
            checkSprite.scaleTo(0f, 0.2f, Easing.OutBounce)
        }

        checkSprite.fadeIn(0.2f)
    }

    override fun onUpdate(clock: TickingClock) {
        if (shouldLoadTexture) {
            shouldLoadTexture = false
            checkSprite.textureRegion = SkinTextures.getAsset("check", "check.png")
        }

        if (backgroundElapsed < BACKGROUND_FADE_DURATION) {
            backgroundElapsed = (backgroundElapsed + clock.elapsedTickTime).coerceAtMost(BACKGROUND_FADE_DURATION)

            val progress = backgroundElapsed / BACKGROUND_FADE_DURATION

            background.backgroundColor = Color4(
                red = backgroundStart.red + (backgroundTarget.red - backgroundStart.red) * progress,
                green = backgroundStart.green + (backgroundTarget.green - backgroundStart.green) * progress,
                blue = backgroundStart.blue + (backgroundTarget.blue - backgroundStart.blue) * progress
            )
        }

        super.onUpdate(clock)
    }

    override fun onInputEvent(event: InputEvent): Boolean {
        if (event !is PointerEvent) {
            return super.onInputEvent(event)
        }

        // Up and cancel events are dispatched to every component, only the finger that pressed this
        // checkbox can release it.
        if (event.action != PointerEvent.Action.Down && (!isPressed || event.pointerId != pressPointerId)) {
            return false
        }

        return when (event.action) {
            PointerEvent.Action.Down -> {
                if (!isPressed) {
                    isPressed = true
                    pressPointerId = event.pointerId
                }
                true
            }
            PointerEvent.Action.Up -> {
                isPressed = false
                value = !value
                true
            }
            PointerEvent.Action.Unknown, PointerEvent.Action.Cancel -> {
                isPressed = false
                false
            }
            PointerEvent.Action.Move -> true
        }
    }

    companion object {
        private const val BACKGROUND_FADE_DURATION = 0.1f
    }
}

//endregion
