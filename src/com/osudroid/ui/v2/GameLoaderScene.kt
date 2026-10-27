package com.osudroid.ui.v2

import com.edlplan.framework.easing.*
import com.osudroid.data.*
import com.osudroid.multiplayer.*
import com.osudroid.multiplayer.api.RoomAPI
import com.osudroid.multiplayer.api.data.PlayerStatus
import com.osudroid.storyboard.parser.StoryboardParser
import com.osudroid.ui.v2.modmenu.LegacyTextButton
import com.osudroid.ui.verktex.*
import com.osudroid.ui.verktex.ModsIndicator
import com.osudroid.ui.verktex.LegacyUnits.vu
import com.osudroid.utils.async
import com.osudroid.utils.ModHashMap
import com.osudroid.utils.updateThread
import com.osudroid.verktex.bridge.RoutedScene
import com.reco1l.andengine.Anchor as LegacyAnchor
import com.reco1l.andengine.component.scaleCenter
import com.reco1l.verktex.Verktex
import com.reco1l.verktex.data.*
import com.reco1l.verktex.input.InputEvent
import com.reco1l.verktex.input.KeyboardEvent
import com.reco1l.verktex.input.PointerEvent
import com.reco1l.verktex.math.Easing as VerktexEasing
import com.reco1l.verktex.math.FloatAnimation
import com.reco1l.verktex.time.TickingClock
import com.reco1l.verktex.ui.*
import com.reco1l.verktex.ui.container.*
import com.reco1l.verktex.ui.container.UILinearContainer.Orientation
import com.reco1l.verktex.ui.form.*
import com.reco1l.verktex.ui.shape.*
import com.reco1l.verktex.ui.text.*
import java.io.File
import java.io.IOException
import kotlin.math.*
import ru.nsu.ccfit.zuev.osu.*
import ru.nsu.ccfit.zuev.osu.game.GameScene
import ru.nsu.ccfit.zuev.osu.helper.StringTable

class GameLoaderScene(private val gameScene: GameScene, private val beatmapInfo: BeatmapInfo, mods: ModHashMap, private val isRestart: Boolean) : UIScene(), RoutedScene {

    private val loaderClock = LoaderClock(isRestart).also {
        // A cancel or a scene change queued ahead of a posted body has replaced this loader.
        it.runIfShown = { body ->
            Verktex.withTreeLock {
                if (current === this) {
                    body.run()
                }
            }
        }
    }
    private var isStarting by loaderClock::isStarting

    private val backgroundSprite: UISprite
    private val dimBox: UIBox
    private val mainContainer: UIContainer
    private lateinit var stepRow: UILinearContainer
    private var shownStepsMask = SHOWN_STEPS_ALL

    // Verktex has no entity modifiers, the fades and the scale are animated in onUpdate.
    private val dimAlpha = FloatAnimation(0.7f, 0f, VerktexEasing.None)
    private val mainAlpha = FloatAnimation(0f, 0f, VerktexEasing.None)
    private val mainScale = FloatAnimation(0.9f, 0f, VerktexEasing.None)

    /**
     * Whether the beatmap has a storyboard. This is determined ahead of loading so that the storyboard
     * step is not shown for beatmaps without one.
     */
    @Volatile
    private var hasStoryboard = false

    private val beatmapStep = LoadingStep("Beatmap")
    private val storyboardStep = LoadingStep("Storyboard")
    private val videoStep = LoadingStep("Video")

    private val beatmapOptions = DatabaseManager.beatmapOptionsTable.getOptions(beatmapInfo.setDirectory)
        ?: BeatmapOptions(beatmapInfo.setDirectory)

    init {
        async {
            hasStoryboard = try {
                StoryboardParser.hasStoryboard(File(beatmapInfo.path))
            } catch (_: IOException) {
                false
            }
        }

        // Background, its texture is set in onShown.
        backgroundSprite = sprite {
            width = fill()
            height = fill()
            scaleType = UISprite.ScaleType.Crop
        }

        // Dim
        dimBox = box {
            width = fill()
            height = fill()
            backgroundColor = Color4.Black
            alpha = 0.7f
        }

        // Beatmap info
        mainContainer = container {
            width = fill()
            height = fill()
            alpha = 0f
            scaleX = 0.9f
            scaleY = 0.9f
            scaleOrigin = Anchor.Center

            if (beatmapInfo.epilepsyWarning) {
                // Verktex passes only the alpha down, so the children get the colour.
                val warningColor = Color4(0xFFFFA726)

                linearContainer {
                    x = 60f.vu.px
                    y = 60f.vu.px
                    spacing = 6f.vu.px

                    sprite {
                        width = 24f.vu.px
                        height = 24f.vu.px
                        y = 2f.vu.px
                        color = warningColor
                        textureRegion = SkinTextures.get("warning", "warning.png")
                    }

                    text {
                        fontSize = LegacyUnits.fontSize("smallFont").vu.px
                        wrapText = false
                        color = warningColor
                        text = StringTable.get(com.osudroid.resources.R.string.epilepsy_warning)
                    }
                }
            }

            linearContainer {
                orientation = Orientation.Vertical
                spacing = 10f.vu.px
                anchor = Anchor.CenterLeft
                origin = Anchor.CenterLeft
                x = 60f.vu.px

                // Title
                text {
                    fontSize = LegacyUnits.fontSize("bigFont").vu.px
                    text = beatmapInfo.titleText
                    width = 700f.vu.px
                    clipping = ClipMode.Bounds
                    wrapText = false
                    // The AndEngine UIText scrolled overflowing text horizontally by default.
                    autoScrollAxes = Axis.X
                    autoScrollSpeed = 30f.vu
                    style = Style { theme -> color = theme.palette.accent }
                }

                // Difficulty
                text {
                    fontSize = LegacyUnits.fontSize("middleFont").vu.px
                    text = beatmapInfo.version
                    width = 700f.vu.px
                    clipping = ClipMode.Bounds
                    wrapText = false
                    autoScrollAxes = Axis.X
                    autoScrollSpeed = 15f.vu
                    style = Style { theme -> color = theme.palette.accent }
                }

                // Creator
                text {
                    fontSize = LegacyUnits.fontSize("middleFont").vu.px
                    wrapText = false
                    text = "by ${beatmapInfo.artistText}"
                    style = Style { theme -> color = theme.palette.accent * 0.9f }
                }

                // Mods
                if (mods.isNotEmpty()) {
                    +ModsIndicator().also { it.mods = mods.values }
                }
            }

            linearContainer {
                orientation = Orientation.Vertical
                anchor = Anchor.BottomLeft
                origin = Anchor.BottomLeft
                x = 60f.vu.px
                y = (-30f).vu.px
                spacing = 30f.vu.px

                // One indicator per loading step. The storyboard and video steps are only shown if they
                // apply to the beatmap.
                stepRow = linearContainer {
                    spacing = 32f.vu.px

                    +beatmapStep
                    +storyboardStep
                    +videoStep
                }

                // The leading icon is sized to the text height, as the AndEngine CompoundText did.
                +LegacyTextButton("Back", "back-arrow", "back-arrow.png").apply {
                    onActionUp = {
                        ResourceManager.getInstance().getSound("click-short-confirm")?.play()
                        cancel()
                    }

                    onActionCancel = { ResourceManager.getInstance().getSound("click-short")?.play() }
                }
            }

            +QuickSettingsLayout()
        }
    }

    /**
     * Cancels loading and goes back to the song menu, or to the multiplayer room if in multiplayer.
     */
    fun cancel() {
        gameScene.cancelLoading()

        if (Multiplayer.isMultiplayer) {
            // Inform that the player has canceled loading.
            RoomAPI.setPlayerStatus(PlayerStatus.NotReady)
            Multiplayer.roomScene?.show()
            return
        }

        val global = GlobalManager.getInstance()
        val songMenu = global.songMenu
        val selectedBeatmap = songMenu.selectedBeatmap

        songMenu.show()

        if (selectedBeatmap != null) {
            songMenu.playMusic(selectedBeatmap.audioPath, selectedBeatmap.previewTime)
        }
    }


    override fun onShown() {
        current = this

        // Textures are registered on the update thread, so the background is set here.
        backgroundSprite.textureRegion = if (Config.isSafeBeatmapBg()) SkinTextures.get("menu-background") else SharedBackground.region()

        mainContainer.paddingBottom = if (Multiplayer.isConnected) (Multiplayer.roomScene!!.chat.buttonHeight + 12f).vu.px else 0f.px

        mainAlpha.animateTo(1f, 0.2f, VerktexEasing.OutCubic)
        mainScale.animateTo(1f, 0.2f, VerktexEasing.OutCubic)
    }

    override fun onHidden() {
        // Nothing to release, the background is shared.
        if (current === this) {
            current = null
        }
    }

    override fun onPerformLayout() {
        // The legacy scene covers the whole window, the safe area is not applied.
        padding = Padding.Zero
        super.onPerformLayout()
    }

    override fun onInputEvent(event: InputEvent): Boolean {
        // Keys arrive on the UI thread. The isShown() check keeps a quick second Back from running cancel() twice.
        if (event is KeyboardEvent && event.action == KeyboardEvent.Action.Down && event.key == KeyboardEvent.Key.Back) {
            updateThread {
                Verktex.withTreeLock {
                    if (isShown()) {
                        cancel()
                    }
                }
            }
            return true
        }

        return super.onInputEvent(event)
    }

    override fun onUpdate(clock: TickingClock) {

        val isBeatmapLoaded = gameScene.isBeatmapLoaded
        val isStoryboardLoading = gameScene.isStoryboardLoading
        val isStoryboardLoaded = isBeatmapLoaded && !isStoryboardLoading

        beatmapStep.isDone = isBeatmapLoaded

        storyboardStep.isVisible = isStoryboardEnabled && hasStoryboard
        storyboardStep.isDone = isStoryboardLoaded

        // Whether the beatmap has a video is only known once it has been loaded.

        videoStep.isVisible = gameScene.isVideoLoading || gameScene.hasVideo()
        videoStep.isDone = !gameScene.isVideoLoading

        updateStepRow()

        loaderClock.update(
            clock.elapsedTickTime,
            Multiplayer.isMultiplayer,
            gameScene.isReadyToStart,
            isStoryboardLoading,
            System.currentTimeMillis(),
            onStart = {
                // This is used instead of getBackgroundBrightness to directly obtain the
                // updated value from the brightness slider.
                val backgroundBrightness = Config.getInt("bgbrightness", 25)

                mainAlpha.animateTo(0f, 0.1f, VerktexEasing.OutExpo)

                dimAlpha.jumpTo(dimAlpha.value)
                dimAlpha.animateTo(1f - backgroundBrightness / 100f, LoaderClock.DIM_FADE_DURATION, VerktexEasing.None)
            },
            onTimeout = {
                ToastLogger.showText(
                    "You have been moved back as you took too long to load the beatmap.",
                    true
                )

                cancel()
            }
        )

        // The end of the dim fade switches to the AndEngine gameplay scene, outside the Verktex update.
        loaderClock.updateDimFade(clock.elapsedTickTime) {

            gameScene.hud.apply {
                alpha = 0f
                scaleX = 0.9f
                scaleY = 0.9f
                scaleCenter = LegacyAnchor.Center

                scaleTo(1f, 0.2f, Easing.OutCubic)
                fadeIn(0.1f, Easing.OutExpo)
            }

            gameScene.start()
        }

        dimBox.alpha = dimAlpha.update(clock)
        mainContainer.alpha = mainAlpha.update(clock)
        mainContainer.scale = mainScale.update(clock)

        super.onUpdate(clock)
    }

    // Verktex lays out hidden components as well, so the row holds the shown steps only, in their order.
    private fun updateStepRow() {
        val mask = (if (beatmapStep.isVisible) 1 else 0) or
            (if (storyboardStep.isVisible) 2 else 0) or
            (if (videoStep.isVisible) 4 else 0)

        if (mask != shownStepsMask) {
            shownStepsMask = mask
            stepRow.clearChildren()

            if (beatmapStep.isVisible) stepRow += beatmapStep
            if (storyboardStep.isVisible) stepRow += storyboardStep
            if (videoStep.isVisible) stepRow += videoStep
        }
    }


    // This is used instead of Config.isEnableStoryboard to directly obtain the updated values from
    // the quick settings.
    private val isStoryboardEnabled
        get() = Config.getInt("bgbrightness", 25) > 2 && Config.getBoolean("enableStoryboard", false)


    /**
     * The indicator of a loading step, which spins while the step is loading and turns into a green
     * circle once it is done.
     */
    private class LoadingStep(name: String) : UILinearContainer() {

        private val spinner = CircularProgressBar().apply {
            width = fill()
            height = fill()
        }

        private val doneCircle = OutlineCircle().apply {
            width = fill()
            height = fill()
            lineWidth = 4f
            color = Color4(0xFF9CFF5A)
            isVisible = false
        }

        var isDone = false
            set(value) {
                if (field != value) {
                    field = value
                    spinner.isVisible = !value
                    doneCircle.isVisible = value
                }
            }

        init {
            orientation = Orientation.Vertical
            spacing = 8f.vu.px

            // Verktex linear containers ignore the anchor of their children, so the circle sits in a
            // container as wide as the step.
            container { width = fill() }.container {
                width = 32f.vu.px
                height = 32f.vu.px
                anchor = Anchor.TopCenter
                origin = Anchor.TopCenter

                +spinner
                +doneCircle
            }

            text {
                fontSize = LegacyUnits.fontSize("smallFont").vu.px
                wrapText = false
                text = name
                anchor = Anchor.TopCenter
                origin = Anchor.TopCenter
                style = Style { theme -> color = theme.palette.accent * 0.9f }
            }
        }
    }

    private inner class QuickSettingsLayout : UIScrollableContainer() {

        init {
            anchor = Anchor.CenterRight
            origin = Anchor.CenterRight
            width = 460f.vu.px
            height = fill()
            x = (-20f).vu.px
            scrollAxes = Axis.Y
            alpha = 0.5f

            linearContainer {
                width = fill()
                spacing = 20f.vu.px
                padding = Padding(0f.px, 20f.vu.px)
                orientation = Orientation.Vertical

                collapsibleCard {
                    width = fill()
                    title = "Beatmap"

                    content.apply {
                        val offsetSlider = FormSlider().apply {
                            label = StringTable.get(com.osudroid.resources.R.string.opt_category_offset)
                            control.min = -250f
                            control.max = 250f
                            value = beatmapOptions.offset.toFloat()
                            defaultValue = beatmapOptions.offset.toFloat()
                            valueFormatter = { "${it.roundToInt()}ms" }

                            onValueChanged = {
                                beatmapOptions.offset = it.roundToInt()
                                DatabaseManager.beatmapOptionsTable.upsert(beatmapOptions)
                            }
                        }
                        +offsetSlider

                        // Verktex linear containers ignore the anchor of their children, so the row sits in a
                        // container as wide as the card.
                        container { width = fill() }.linearContainer {
                            spacing = 10f.vu.px
                            padding = Padding(0f.px, 16f.vu.px)
                            anchor = Anchor.TopCenter
                            origin = Anchor.TopCenter

                            // The leading icon is sized to the text height, as the AndEngine CompoundText did.
                            fun StepButton(step: Int) = object : LegacyTextButton(abs(step).toString(), if (step < 0) "minus" else "plus", if (step < 0) "minus.png" else "plus.png") {
                                override fun onApplyLegacySizes() {
                                    super.onApplyLegacySizes()
                                    height = 42f.vu.px
                                    padding = Padding(12f.vu.px, 0f.px, 24f.vu.px, 0f.px)
                                }
                            }.apply {

                                onActionUp = {
                                    offsetSlider.value += step
                                }
                            }.also { +it }

                            StepButton(-5)
                            StepButton(-1)
                            StepButton(1)
                            StepButton(5)
                        }
                    }

                    onExpandStatusChange = {
                        beatmapCardCollapsed = !it
                    }

                    if (beatmapCardCollapsed) {
                        collapse(true)
                    }
                }

                collapsibleCard {
                    width = fill()
                    title = "Settings"

                    content.apply {

                        +IntPreferenceSlider("bgbrightness", 25).apply {
                            label = StringTable.get(com.osudroid.resources.R.string.opt_bgbrightness_title)
                            control.min = 0f
                            control.max = 100f
                            control.onStopDragging = {
                                if (!isStarting) {
                                    dimAlpha.animateTo(0.7f, 0.1f, VerktexEasing.None)
                                }
                            }
                            valueFormatter = { "${it.roundToInt()}%" }
                            onValueChanged = {
                                Config.setBackgroundBrightness(it / 100f)

                                // Storyboard and video should not be enabled if the background brightness is too low,
                                // so we trigger a reload when changing brightness.
                                gameScene.loadStoryboard(beatmapInfo)
                                gameScene.loadVideo(beatmapInfo)

                                if (!isStarting) {
                                    dimAlpha.jumpTo(1f - it / 100f)
                                }
                            }
                        }

                        +PreferenceCheckbox("enableStoryboard").apply {
                            label = StringTable.get(com.osudroid.resources.R.string.opt_enableStoryboard_title)
                            onValueChanged = {
                                gameScene.loadStoryboard(beatmapInfo)
                            }
                        }

                        +PreferenceCheckbox("enableVideo").apply {
                            label = StringTable.get(com.osudroid.resources.R.string.opt_video_title)
                            onValueChanged = {
                                gameScene.loadVideo(beatmapInfo)
                            }
                        }

                        +PreferenceCheckbox("showscoreboard").apply {
                            label = StringTable.get(com.osudroid.resources.R.string.opt_show_scoreboard_title)
                        }
                    }

                    onExpandStatusChange = {
                        settingsCardCollapsed = !it
                    }

                    if (settingsCardCollapsed) {
                        collapse(true)
                    }
                }
            }
        }

        override fun onInputEvent(event: InputEvent): Boolean {
            // Only touches inside the panel count, as onAreaTouched was only called for those.
            if (event is PointerEvent && containsScenePoint(event.x, event.y)) {
                alpha = 1f
                loaderClock.onPanelTouched(System.currentTimeMillis())
            }

            return super.onInputEvent(event)
        }

        override fun onUpdate(clock: TickingClock) {

            alpha = loaderClock.fadePanel(alpha, clock.elapsedTickTime, System.currentTimeMillis())

            super.onUpdate(clock)
        }

    }

    /**
     * The start decision of [onUpdate], the multiplayer timeout and the fade of the quick settings,
     * apart from the scene so they can be tested without a [GameScene].
     */
    internal class LoaderClock(private val isRestart: Boolean) {

        var lastTimeTouched = System.currentTimeMillis()
        var isStarting = false
        var multiplayerLoadElapsed = 0f

        var fadeTimeout = if (isRestart) 500L else 2000L
        var minimumTimeout = if (isRestart) 500L else 2000L

        /**
         * Runs the hand-over and the timeout cancel after the Verktex update: both change the
         * scene, which the scene being updated must not do. Replaceable for tests.
         */
        var post: (Runnable) -> Unit = { updateThread(it) }

        /**
         * Runs a posted body only while the loader is still the shown one. Replaceable for tests.
         */
        var runIfShown: (Runnable) -> Unit = { it.run() }

        private var hasTimedOut = false
        private var dimFadeElapsed = 0f
        private var isHandedOver = false

        /**
         * Starts the loader once it may, calling [onStart] right away, or posts [onTimeout] once
         * the multiplayer load took too long.
         */
        fun update(deltaTimeSec: Float, isMultiplayer: Boolean, isReadyToStart: Boolean, isStoryboardLoading: Boolean, now: Long, onStart: () -> Unit, onTimeout: Runnable) {

            if (!isStarting && !hasTimedOut) {

                if (isMultiplayer && !isReadyToStart) {
                    multiplayerLoadElapsed += deltaTimeSec

                    if (multiplayerLoadElapsed >= MULTIPLAYER_LOAD_TIMEOUT_SEC) {
                        hasTimedOut = true
                        post(Runnable { runIfShown(onTimeout) })
                        return
                    }
                }

                if (isReadyToStart) {

                    // Multiplayer will skip the minimum timeout if it's ready to start. It also does not wait for the
                    // storyboard, as all players must start at the same time.
                    val isTimeoutElapsed = now - lastTimeTouched > minimumTimeout

                    if (isMultiplayer || (isTimeoutElapsed && !isStoryboardLoading)) {
                        isStarting = true
                        onStart()
                    }

                } else {
                    lastTimeTouched = now
                }
            }
        }

        /**
         * Counts the dim fade once started and posts [onFadeEnd] when it ends, as its after() did.
         */
        fun updateDimFade(deltaTimeSec: Float, onFadeEnd: Runnable) {
            if (!isStarting || isHandedOver) {
                return
            }

            dimFadeElapsed += deltaTimeSec

            if (dimFadeElapsed >= DIM_FADE_DURATION) {
                isHandedOver = true
                post(Runnable { runIfShown(onFadeEnd) })
            }
        }

        fun onPanelTouched(now: Long) {
            lastTimeTouched = now

            // When the player is restarting, and they touch the layout, assume they want to change settings.
            // In that case, show this loading scene longer.
            if (isRestart) {
                fadeTimeout = 1500L
                minimumTimeout = 1500L
            }
        }

        /**
         * Returns the alpha of the quick settings after [deltaTimeSec].
         */
        fun fadePanel(alpha: Float, deltaTimeSec: Float, now: Long): Float {

            val elapsed = now - lastTimeTouched

            if (alpha > 0.5f && elapsed > fadeTimeout) {
                return alpha - deltaTimeSec * 1.5f
            }

            return alpha
        }

        companion object {
            const val DIM_FADE_DURATION = 0.2f
        }
    }

    companion object {
        private var beatmapCardCollapsed = false
        private var settingsCardCollapsed = false

        private const val MULTIPLAYER_LOAD_TIMEOUT_SEC = 60f

        // The three steps are added to their row at build.
        private const val SHOWN_STEPS_ALL = 0b111

        // The loader Verktex shows, set while it is the shown scene.
        @Volatile
        private var current: GameLoaderScene? = null

        /**
         * Whether a loader is Verktex's scene.
         */
        @JvmStatic
        fun isShown() = current != null
    }
}