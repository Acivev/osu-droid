package com.osudroid.ui.v2.menu.main

import android.util.Log
import android.content.Intent
import android.net.Uri
import com.osudroid.beatmaplisting.BeatmapListing
import com.osudroid.beatmaps.BeatmapCache
import com.osudroid.beatmaps.timings.EffectControlPoint
import com.osudroid.beatmaps.timings.TimingControlPoint
import com.osudroid.resources.R
import com.osudroid.ui.BannerManager
import com.osudroid.ui.v1.SettingsFragment
import com.osudroid.ui.v2.multi.LobbyScene
import com.osudroid.ui.verktex.IPressable
import com.osudroid.ui.verktex.ISkinnable
import com.osudroid.ui.verktex.LegacyUnits
import com.osudroid.ui.verktex.LegacyUnits.vu
import com.osudroid.ui.verktex.SkinTextures
import com.osudroid.verktex.bridge.EngineRouter
import com.osudroid.verktex.bridge.RoutedScene
import com.reco1l.osu.ui.HorizontalMessageDialog
import ru.nsu.ccfit.zuev.osuplus.BuildConfig
import com.osudroid.multiplayer.Multiplayer
import com.osudroid.utils.async
import com.osudroid.utils.mainThread
import com.osudroid.utils.updateThread
import com.osudroid.data.BeatmapInfo
import com.reco1l.verktex.Verktex
import com.reco1l.verktex.data.Anchor
import com.reco1l.verktex.data.ClipMode
import com.reco1l.verktex.data.Color4
import com.reco1l.verktex.data.Padding
import com.reco1l.verktex.data.fill
import com.reco1l.verktex.data.px
import com.reco1l.verktex.graphics.GraphicsRenderer
import com.reco1l.verktex.graphics.GraphicsTexture
import com.reco1l.verktex.input.InputEvent
import com.reco1l.verktex.input.KeyboardEvent
import com.reco1l.verktex.input.PointerEvent
import com.reco1l.verktex.math.Easing
import com.reco1l.verktex.math.FloatAnimation
import com.reco1l.verktex.texture.Textures
import com.reco1l.verktex.time.TickingClock
import com.reco1l.verktex.ui.UIScene
import com.reco1l.verktex.ui.UISprite
import com.reco1l.verktex.ui.UISprite.ScaleType
import com.reco1l.verktex.ui.container.UIClickableContainer
import com.reco1l.verktex.ui.container.UIContainer
import com.reco1l.verktex.ui.dialog.UIConfirmDialog
import com.reco1l.verktex.ui.shape.UIBox
import com.reco1l.verktex.ui.shape.UICircle
import com.reco1l.verktex.ui.text.UIText
import ru.nsu.ccfit.zuev.audio.Status
import ru.nsu.ccfit.zuev.osu.Config
import ru.nsu.ccfit.zuev.osu.GlobalManager
import ru.nsu.ccfit.zuev.osu.LibraryManager
import ru.nsu.ccfit.zuev.osu.ResourceManager
import ru.nsu.ccfit.zuev.osu.ToastLogger
import ru.nsu.ccfit.zuev.osu.helper.StringTable
import ru.nsu.ccfit.zuev.osu.menu.LoadingScreen
import ru.nsu.ccfit.zuev.osu.online.OnlineManager
import ru.nsu.ccfit.zuev.osu.online.OnlinePanel
import ru.nsu.ccfit.zuev.osu.online.OnlineScoring
import ru.nsu.ccfit.zuev.osu.scoring.Replay
import java.io.File
import java.io.IOException
import java.util.LinkedList
import android.os.Handler
import android.os.Looper

/**
 * The main menu. All sizes and positions are kept in the virtual units of the legacy menu and
 * converted to window pixels in [onPerformLayout].
 */
class MainMenuV2 : UIScene(), ISkinnable, RoutedScene {

    // Virtual size of the window; read outside of init only, the window has no size before the first frame.
    private val virtualWidth get() = Verktex.graphics.window.windowWidth / LegacyUnits.scale
    private val virtualHeight get() = Config.getRES_HEIGHT().toFloat()

    // Overlays
    private var menuBackground: BackgroundSprite
    private val dimBox: UIBox
    private val sideFlashes: SideFlashes
    private val fadeInOverlay: UIBox

    // Logo
    private val logo: Logo

    // Menu buttons
    private val buttonClipContainer: UIContainer
    private val btnFirst: MenuButton
    private val btnSecond: MenuButton
    private val btnThird: MenuButton
    private val buttons get() = listOf(btnFirst, btnSecond, btnThird)

    private val versionBox: UIClickableContainer
    private val versionText: UIText
    private val downloader: PressableSprite

    // Debug build banner
    private var devOverlay: UISprite? = null
    private var devTextShadow: UIText? = null
    private var devText: UIText? = null

    // Textures, looked up on the update thread (see onSkinChanged)
    private var playTex: GraphicsTexture.Region? = null
    private var soloTex: GraphicsTexture.Region? = null
    private var optionsTex: GraphicsTexture.Region? = null
    private var multiTex: GraphicsTexture.Region? = null
    private var exitTex: GraphicsTexture.Region? = null
    private var backTex: GraphicsTexture.Region? = null
    private val playSound
            by lazy { ResourceManager.getInstance().loadSound("menuhit", "sfx/menuhit.ogg", false) }

    /** Set when the skin regions must be looked up again on the next update. */
    private var texturesInvalid = true

    // Animations
    private val dimAlpha = FloatAnimation(0.45f, 0f)
    private val fadeInAlpha = FloatAnimation(1f, 0f)
    private val panelAlpha = FloatAnimation(1f, 0f)
    /** The logo's horizontal offset from the window centre. */
    private val logoOffsetX = FloatAnimation(0f, 0f)

    /** Seconds left of the kiai dim dip before the dim goes back to its resting value. */
    private var dimDipRemaining = 0f

    /** Left edge of the clip container, repositioned in openMenu(). */
    private var menuX = 0f

    /** Scale of the menu buttons, computed in openMenu(). */
    private var buttonScale = 1f

    // Timing
    private var timingControlPoints = LinkedList<TimingControlPoint>()
    private var effectControlPoints = LinkedList<EffectControlPoint>()
    private var currentTimingPoint: TimingControlPoint? = null
    private var currentEffectPoint: EffectControlPoint? = null
    private var bpmLength = 1000f
    private var kiaiActive = false
    private var musicStarted = false

    // Flash
    /**
     * Wall-clock beat accumulator, the legacy beat pass time.
     * Advances every frame and fires an idle beat when no music is playing,
     * so the logo keeps pulsing at the current BPM (default 60 BPM / 1000 ms).
     */
    private var beatPassTime = 0f

    /**
     * state only needs the previous beat index now; animation handled by the scene's animations.
     */
    private var prevBeat = -1

    private val starFountain: StarFountain

    // State
    private var menuOpen = false
    private var isSecondMenu = false

    /**
     * tracks when beatmap changes for bg crossfade
     */
    private var lastBgAudioPath: String? = null

    /**
     * seconds the menu has been open without interaction
     */
    private var menuOpenTime = 0f

    var isOnExitAnim = false

    // Guards initOnlinePanel() so it only runs once per process lifetime,
    // preventing duplicate panels on scene re-attach (onLoadComplete fires each time).
    private var onlinePanelInitialised = false

    /**
     * Whether onLoadComplete() runs on the next update. Set whenever the scene is shown, the Verktex
     * scene has no load callback of its own.
     */
    private var isLoadPending = true

    /**
     * Counts the loaded beatmap backgrounds; each gets its own texture key so the one fading out
     * stays valid until it is removed.
     */
    private val backgroundGeneration = java.util.concurrent.atomic.AtomicInteger()

    /**
     * set to true after a skin reload so the background texture is refreshed
     * on the next frame instead of doing two lookups every frame unconditionally.
     */
    var bgNeedsRefresh = true

    /**
     * Current beatmap (owned by MainMenuV2)
     */
    var beatmapInfo: BeatmapInfo? = null

    // Music player panel
    private val musicPlayerPanel = MusicPlayerPanel()

    // Attached once online, see initOnlinePanel() and loadBannerSprite()
    private var onlinePanel: OnlinePanel? = null
    private var bannerSprite: BannerManager.BannerSprite? = null

    init {
        // Background
        menuBackground = BackgroundSprite(null).also { this += it }

        // Dark dim
        dimBox = UIBox().also {
            it.width = fill()
            it.height = fill()
            it.backgroundColor = Color4.Black
            it.alpha = 0.45f

            this += it
        }

        // Kiai side flashes
        sideFlashes = SideFlashes().also { it.attachTo(this) }

        // Clip container: its left edge = logo's right edge, so anything to the left
        // of the logo is scissored clipped and never rendered no animation tricks needed.
        // Attached BEFORE the logo so logo remains on top for touch priority.
        buttonClipContainer = UIContainer().also { c ->
            c.clipping = ClipMode.Bounds
            c.width = fill()
            c.height = fill()

            this += c
        }

        btnFirst = MenuButton { onFirstButton() }.also { buttonClipContainer += it }
        btnSecond = MenuButton { onSecondButton() }.also { buttonClipContainer += it }
        btnThird = MenuButton { onThirdButton() }.also { buttonClipContainer += it }

        // Logo
        logo = Logo(onTap = ::onLogoTap, onKiaiBurst = ::onKiaiBurst).also {
            it.anchor = Anchor.Center
            it.origin = Anchor.Center

            this += it
        }

        versionText = UIText().apply {
            text = "osu!droid-GLES-${BuildConfig.VERSION_NAME}"
            color = Color4.White
            wrapText = false
        }

        // The box wraps the text with 10/15 horizontal and 6 vertical padding, keeping it centred.
        versionBox = UIClickableContainer().apply {
            anchor = Anchor.BottomLeft
            origin = Anchor.BottomLeft
            backgroundColor = Color4(0f, 0f, 0f, 0.5f)
            onActionUp = {
                mainThread {
                    HorizontalMessageDialog()
                        .setTitle("About")
                        .setMessage(
                            "<h1>osu!droid-GLES</h1>\n" +
                            "<h5>Version ${BuildConfig.VERSION_NAME}</h5>\n" +
                            "<p>Made by osu!droid team<br>osu! is © peppy 2007-2026</p>\n" +
                            "<br>\n" +
                            "<a href=\"https://osu.ppy.sh\">Visit official osu! website ↗</a>\n" +
                            "<br><br>\n" +
                            "<a href=\"https://osudroid.moe\">Visit official osu!droid website ↗</a>\n" +
                            "<br><br>\n" +
                            "<a href=\"https://discord.gg/nyD92cE\">Join the official Discord server ↗</a>\n",
                            true
                        )
                        .addButton("Changelog") { dialog ->
                            dialog.dismiss()
                            try {
                                val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://osudroid.moe/changelog/gles/latest"))
                                GlobalManager.getInstance().mainActivity.startActivity(intent)
                            } catch (e: Exception) {
                                Log.e("MainMenuV2", "Failed to open changelog", e)
                            }
                        }
                        .addButton("Close") { it.dismiss() }
                        .show()
                }
            }
            this += versionText
        }

        this += versionBox

        // Beatmap downloader
        downloader = PressableSprite(ignoreWhenHidden = false) {
            mainThread { BeatmapListing().show() }
        }.also {
            it.anchor = Anchor.CenterRight
            it.origin = Anchor.CenterRight
            this += it
        }

        // Music player panel
        this += musicPlayerPanel
        musicPlayerPanel.onTrackChanged = {
            musicStarted = false
            reloadTimingPoints()
        }
        musicPlayerPanel.onSeek = {
            prevBeat = -1
            beatPassTime = 0f
            reloadTimingPoints()
            // Flush any visual state that stacked up while the seekbar was held:
            // reset the dim box, silence the side flashes, and stop the fountain.
            dimDipRemaining = 0f
            dimAlpha.jumpTo(0.45f)
            sideFlashes.fadeOut(0f) // instant no leftover flash
            starFountain.stop()
            kiaiActive = false
        }

        // Star fountains
        starFountain = StarFountain().also { it.attachTo(this) }

        // Fade-in black overlay
        fadeInOverlay = UIBox().also {
            it.width = fill()
            it.height = fill()
            it.backgroundColor = Color4.Black
            it.alpha = 1f
            this += it
        }

        // Debug build banner
        if (BuildConfig.DEBUG) {
            devOverlay = UISprite().also {
                it.anchor = Anchor.BottomCenter
                it.origin = Anchor.BottomCenter
                this += it
            }
            devTextShadow = UIText().also {
                it.text = "DEVELOPMENT BUILD"
                it.color = Color4(0f, 0f, 0f, 0.5f)
                it.wrapText = false
                it.anchor = Anchor.BottomCenter
                it.origin = Anchor.BottomCenter
                this += it
            }
            devText = UIText().also {
                it.text = "DEVELOPMENT BUILD"
                it.color = Color4(1f, 237f / 255f, 0f, 1f)
                it.wrapText = false
                it.anchor = Anchor.BottomCenter
                it.origin = Anchor.BottomCenter
                this += it
            }
        }
    }

    override fun onDraw(renderer: GraphicsRenderer) {
        // UIScene.onDraw without assigning the safe-area padding: the legacy scene covers the whole
        // window, and toggling the padding every frame would invalidate the measure each time.
        onPerformLayout()

        val clip = renderer.clipBounds
        for (child in children) {
            if (child.intersectsSceneRect(clip.x, clip.y, clip.z, clip.w)) {
                onDrawChild(renderer, child)
            }
        }
    }

    override fun onPerformLayout() {
        logo.performLayout()
        logo.x = logoOffsetX.value.vu.px

        musicPlayerPanel.performLayout()
        bannerSprite?.performLayout()

        buttonClipContainer.x = menuX.vu.px
        for (btn in buttons) {
            btn.width = ((btn.textureRegion?.width ?: 0f) * buttonScale).vu.px
            btn.height = ((btn.textureRegion?.height ?: 0f) * buttonScale).vu.px
            btn.x = btn.positionX.value.vu.px
            btn.y = btn.positionY.vu.px
        }

        val smallFont = LegacyUnits.fontSize("smallFont").vu.px
        versionText.fontSize = smallFont
        versionBox.x = 10f.vu.px
        versionBox.y = (-10f).vu.px
        versionBox.padding = Padding(left = 10f.vu.px, top = 6f.vu.px, right = 15f.vu.px, bottom = 6f.vu.px)
        versionBox.radius = 12f.vu.px

        downloader.setLegacySize()

        devOverlay?.let { overlay ->
            overlay.setLegacySize()
            val overlayH = overlay.textureRegion?.height ?: 0f

            devTextShadow?.fontSize = smallFont
            devTextShadow?.x = 2f.vu.px
            devTextShadow?.y = (-overlayH - 1f + 2f).vu.px

            devText?.fontSize = smallFont
            devText?.y = (-overlayH - 1f).vu.px
        }

        super.onPerformLayout()
    }

    private fun UISprite.setLegacySize() {
        width = (textureRegion?.width ?: 0f).vu.px
        height = (textureRegion?.height ?: 0f).vu.px
    }

    override fun onSkinChanged() {
        texturesInvalid = true
        bgNeedsRefresh = true
    }

    private fun loadTextures() {
        playTex = SkinTextures.get("play", "play.png")
        soloTex = SkinTextures.get("solo", "solo.png")
        optionsTex = SkinTextures.get("options", "options.png")
        multiTex = SkinTextures.get("multi", "multi.png")
        exitTex = SkinTextures.get("exit", "exit.png")
        backTex = SkinTextures.get("back", "back.png")
        applyButtonTextures()

        downloader.textureRegion = SkinTextures.get("beatmap_downloader", "beatmap_downloader.png")
        devOverlay?.textureRegion = SkinTextures.get("dev-build-overlay", "dev-build-overlay.png")
    }

    private fun onLoadComplete() {
        // Reset overlay to opaque so it covers the background reload on every scene entry.
        fadeInAlpha.jumpTo(1f)
        fadeInAlpha.animateTo(0f, 0.5f, Easing.OutCubic)

        // Force background to always reload so stale/detached sprites are replaced.
        lastBgAudioPath = null

        reloadTimingPoints()
        val songService = GlobalManager.getInstance().songService

        if (songService != null && songService.status == Status.STOPPED)
            musicStarted = false

        if (!onlinePanelInitialised) {
            onlinePanelInitialised = true
            initOnlinePanel()
        }
    }

    override fun onInputEvent(event: InputEvent): Boolean {
        if (event is KeyboardEvent && event.action == KeyboardEvent.Action.Down && event.key == KeyboardEvent.Key.Back) {
            showExitDialog()
            return true
        }

        return super.onInputEvent(event)
    }

    override fun onUpdate(clock: TickingClock) {
        val deltaTimeSec = clock.elapsedTickTime

        if (texturesInvalid) {
            texturesInvalid = false
            loadTextures()
        }

        if (isLoadPending) {
            isLoadPending = false
            onLoadComplete()
        }

        super.onUpdate(clock)

        // Animations keep running during the exit animation.
        if (dimDipRemaining > 0f) {
            dimDipRemaining -= deltaTimeSec
            if (dimDipRemaining <= 0f) dimAlpha.animateTo(0.45f, 0.8f, Easing.OutSine)
        }
        dimBox.alpha = dimAlpha.update(clock)
        fadeInOverlay.alpha = fadeInAlpha.update(clock)
        musicPlayerPanel.alpha = panelAlpha.update(clock)
        logoOffsetX.update(clock)

        // Refresh background texture only when flagged (e.g. after a skin reload).
        // Avoids two lookups every frame for a value that almost never changes. Done before the
        // early returns below, it also sets the initial background.
        if (bgNeedsRefresh) {
            bgNeedsRefresh = false

            val texture = menuBackground.textureKey?.let { Textures[it] }
                ?: SkinTextures.get("menu-background")

            if (texture != null)
                menuBackground.textureRegion = texture
        }

        // During the exit animation let the animations (position/alpha) keep running
        // above, but skip all game/music logic to prevent music from restarting,
        // kiai from cancelling animations, background crossfades, etc.
        if (isOnExitAnim) return

        val songService = GlobalManager.getInstance().songService ?: return
        if (!musicStarted && songService.status == Status.STOPPED) {
            songService.play()
            if (songService.status != Status.STOPPED) {
                songService.volume = Config.getBgmVolume()
                musicStarted = true
            }
        }

        // Kiai cannot be active when there is no playback, clear it so CatJam
        // and other kiai visuals don't persist beyond the end of a track.
        if (songService.status == Status.STOPPED && kiaiActive) {
            kiaiActive = false
            onKiaiEnd()
        }

        val isSeeking = musicPlayerPanel.isDraggingSeek
        val songPos = songService.position

        if (!isSeeking) {
            val prevTimingPoint = currentTimingPoint
            while (timingControlPoints.isNotEmpty() &&
                songPos.toDouble() > (timingControlPoints.peek()?.time ?: Double.MAX_VALUE))
                currentTimingPoint = timingControlPoints.poll()

            // When the timing point changes, reset prevBeat so the first beat of the new
            // section doesn't fire a spurious onBeat() (which would jump spectrum indexOffset).
            if (currentTimingPoint !== prevTimingPoint) prevBeat = -1

            currentTimingPoint?.let { bpmLength = it.msPerBeat.toFloat() }

            while (effectControlPoints.isNotEmpty() &&
                songPos.toDouble() > (effectControlPoints.peek()?.time ?: Double.MAX_VALUE))

                currentEffectPoint = effectControlPoints.poll()

            val nowKiai = currentEffectPoint?.isKiai == true
            if (!kiaiActive && nowKiai) onKiaiStart()
            else if (kiaiActive && !nowKiai) onKiaiEnd()

            kiaiActive = nowKiai

            // Only drive the fountain while music is actively playing — songPos deltas
            // are used to track burst duration and the loop-sound retrigger window, so a
            // frozen position (paused/stopped) would leave emission and loop audio running
            // indefinitely.
            if (songService.status == Status.PLAYING) {
                starFountain.update(songPos)
            } else if (starFountain.isActive) {
                starFountain.stop()
            }

            if (musicStarted && songService.status != Status.PLAYING && songPos >= songService.getLength()) {
                musicStarted = false   // allow the new preloaded track to start
                musicControl(MusicOption.NEXT)
            }

            if (bpmLength > 0f) {
                if (songService.status == Status.PLAYING) {
                    val timingPointStart = (currentTimingPoint?.time ?: 0.0).toFloat()
                    val timingPointSongPos = (songPos - timingPointStart).coerceAtLeast(0f)
                    beatPassTime = timingPointSongPos % bpmLength
                    val beat = (timingPointSongPos / bpmLength).toInt()
                    if (beat != prevBeat) {
                        prevBeat = beat
                        sideFlashes.onNewBeat(
                            beat = beat,
                            kiaiActive = kiaiActive,
                            bpmMs = bpmLength,
                            fft = songService.spectrum,
                            timeSignature = currentTimingPoint?.timeSignature ?: 4
                        )
                        logo.onBeat()
                    }
                } else {
                    // No music / paused, advance wall-clock accumulator at the current BPM
                    // (defaults to 1000 ms = 60 BPM), as the legacy menu did.
                    beatPassTime += deltaTimeSec * 1000f
                    if (beatPassTime >= bpmLength) {
                        beatPassTime %= bpmLength
                        // pulseBeat() sets an explicit scale kick so the logo visually
                        // bounces even though FFT energy is zero (paused/stopped).
                        logo.pulseBeat()
                        // Side flashes are intentionally not fired when idle
                        // (no music = no amplitude data to drive them).
                    }
                }
            }
        }

        logo.kiaiActive = kiaiActive
        logo.update(
            dt = deltaTimeSec,
            songPos = songPos,
            bpmMs = bpmLength,
            timingOffset = currentTimingPoint?.time?.toInt() ?: 0,
            fft = songService.spectrum,
            isPlaying = songService.status == Status.PLAYING
        )

        if (menuOpen) {
            menuOpenTime += deltaTimeSec
            if (menuOpenTime >= 5f)
                closeMenu()
        }

        musicPlayerPanel.update()
    }


    // Kiai
    private fun onKiaiStart() {
        // Quick dip, then back to the resting dim.
        dimAlpha.animateTo(0.25f, 0.15f, Easing.OutQuad)
        dimDipRemaining = 0.15f

        // Fire star fountains with a random direction (-1 / 0 / 1)
        val songPos = GlobalManager.getInstance().songService?.position ?: 0
        starFountain.shoot(songPos)
    }
    private fun onKiaiEnd() {
        dimDipRemaining = 0f
        dimAlpha.animateTo(0.45f, 0.3f, Easing.OutSine)
    }
    private fun onKiaiBurst(cx: Float, cy: Float, radius: Float) {
        this += ExpandingRing(cx, cy, radius)
    }

    // Background crossfade
    private fun crossfadeBackground(info: BeatmapInfo) {
        val generation = backgroundGeneration.incrementAndGet()

        async {
            val source = if (info.backgroundFilename != null && !Config.isSafeBeatmapBg()) {
                SkinTextures.decode(File(info.backgroundPath))
            } else null

            updateThread {
                // A newer crossfade started while this one was decoding; its result wins.
                if (generation != backgroundGeneration.get()) return@updateThread

                val key = "::background#$generation"

                val fileTex = source?.let {
                    try {
                        SkinTextures.register(key, it)
                    }
                    catch (_: Exception) { null }
                }

                val newTex = fileTex
                    ?: SkinTextures.get("menu-background")
                    ?: return@updateThread

                Verktex.withTreeLock {
                    val oldBg = menuBackground
                    val newBg = BackgroundSprite(if (fileTex != null) key else null).apply {
                        textureRegion = newTex
                        fade.jumpTo(0f)
                        fade.animateTo(1f, 1.5f, Easing.OutCubic)
                        alpha = 0f
                    }
                    menuBackground = newBg
                    this@MainMenuV2 += newBg
                    newBg.moveToBack()
                    oldBg.fadeOutAndRemove()
                }
            }
        }
    }

    // Logo tap
    private fun onLogoTap() {
        if (isOnExitAnim) return  // ignore taps during the exit animation

        playSound?.play()

        if (menuOpen) closeMenu()
        else openMenu()
    }

    private fun openMenu() {
        menuOpen = true
        menuOpenTime = 0f

        val nativeH = btnSecond.textureRegion?.height?.takeIf { it > 0f } ?: 80f
        val scale = (Logo.LOGO_SIZE * 0.9f / (3f * nativeH + 80f)).coerceIn(0.2f, 2f)
        buttonScale = scale

        // Center the whole composition (logo disc + buttons)
        val overlapPx = Logo.LOGO_SIZE * MENU_OVERLAP
        val btnW = btnFirst.textureRegion?.width?.takeIf { it > 0f }?.times(scale) ?: (Logo.LOGO_SIZE * 2.5f)
        val totalW = Logo.LOGO_SIZE + btnW - overlapPx
        val compLeft = virtualWidth / 2f - totalW / 2f
        val logoX = compLeft + Logo.LOGO_SIZE / 2f
        menuX = compLeft + Logo.LOGO_SIZE - overlapPx

        // Clip container starts at menuX.
        // Buttons themselves are at x=0 relative to container.
        // The logo (rendered on top) covers the overlap region,
        // preserving the layered look while guaranteeing nothing renders left of menuX.

        // Use RENDERED heights (native × scale) for gap/spacing.
        val secondH = (btnSecond.textureRegion?.height ?: 0f) * scale
        val firstH = (btnFirst.textureRegion?.height ?: 0f) * scale
        val gap = 40f * scale

        val secondY = (virtualHeight - secondH) / 2f
        val firstY = secondY - firstH - gap
        val thirdY = secondY + secondH + gap

        // Buttons at x=0 inside the container.
        // Slide in from slightly off-left, then settle at 0.
        btnFirst.positionY = firstY
        btnSecond.positionY = secondY
        btnThird.positionY = thirdY

        for (btn in buttons) {
            btn.positionX.jumpTo(-100f)
            btn.positionX.animateTo(0f, 0.5f, Easing.OutElastic)
            btn.fade.jumpTo(0f)
            btn.fade.animateTo(0.9f, 0.5f, Easing.OutCubic)
        }

        // Slide logo to its computed centred position.
        logoOffsetX.animateTo(logoX - virtualWidth / 2f, 0.3f, Easing.OutExpo)
    }

    private fun closeMenu() {
        menuOpen = false
        menuToFirst()
        isSecondMenu = false

        // Fade buttons before logo moves far enough to uncover the clip boundary.
        for (btn in buttons) {
            btn.positionX.jumpTo(btn.positionX.value)
            btn.fade.animateTo(0f, 0.15f, Easing.OutQuad)
        }

        logoOffsetX.animateTo(0f, 0.3f, Easing.OutCubic)
    }

    // Button actions
    private fun onFirstButton() {
        if (isSecondMenu) {
            closeMenu()
            GlobalManager.getInstance().songService?.isGaming = true
            async {
                LoadingScreen.show()
                GlobalManager.getInstance().mainActivity.checkNewSkins()
                GlobalManager.getInstance().mainActivity.loadBeatmapLibrary()
                if (LibraryManager.getLibrary().isEmpty()) {
                    updateThread {
                        GlobalManager.getInstance().songService?.isGaming = false
                        showSelf()
                        BeatmapListing().show()
                    }
                } else {
                    updateThread {
                        musicControl(MusicOption.PLAY)
                        Verktex.withTreeLock {
                            val songMenu = GlobalManager.getInstance().songMenu
                            songMenu.reload()
                            songMenu.show()
                            songMenu.select()
                        }
                    }
                }
            }
        } else {
            menuToSecond()
        }
    }

    private fun onSecondButton() {
        if (isSecondMenu) {
            closeMenu()
            if (!OnlineManager.getInstance().isStayOnline) {
                ToastLogger.showText(StringTable.format(R.string.multiplayer_not_online), true)
                return
            }
            GlobalManager.getInstance().songService?.isGaming = true
            Multiplayer.isMultiplayer = true
            async {
                LoadingScreen.show()
                GlobalManager.getInstance().mainActivity.checkNewSkins()
                GlobalManager.getInstance().mainActivity.loadBeatmapLibrary()
                updateThread {
                    GlobalManager.getInstance().songMenu.reload()
                    GlobalManager.getInstance().engine.scene = LobbyScene()
                }
            }
        } else {
            closeMenu()
            GlobalManager.getInstance().songService?.isGaming = true
            mainThread { SettingsFragment().show() }
        }
    }

    private fun onThirdButton() {
        if (isSecondMenu) menuToFirst()
        else showExitDialog()
    }

    private fun menuToFirst() {
        if (!isSecondMenu) return

        isSecondMenu = false
        applyButtonTextures()
    }
    private fun menuToSecond() {
        if (isSecondMenu) return

        isSecondMenu = true
        menuOpenTime = 0f
        applyButtonTextures()
    }

    private fun applyButtonTextures() {
        if (isSecondMenu) {
            btnFirst.textureRegion = soloTex
            btnSecond.textureRegion = multiTex
            btnThird.textureRegion = backTex
        } else {
            btnFirst.textureRegion = playTex
            btnSecond.textureRegion = optionsTex
            btnThird.textureRegion = exitTex
        }
    }

    // Timing
    fun reloadTimingPoints() {
        val info = beatmapInfo ?: return
        async {
            try {
                val beatmap = BeatmapCache.getBeatmap(info, false)
                val allTiming = LinkedList(beatmap.controlPoints.timing.controlPoints)
                val allEffect = LinkedList(beatmap.controlPoints.effect.controlPoints)
                val pos = GlobalManager.getInstance().songService?.position ?: 0

                var tp: TimingControlPoint? = null
                while (allTiming.isNotEmpty() && pos.toDouble() > (allTiming.peek()?.time ?: Double.MAX_VALUE))
                    tp = allTiming.poll()

                var ep: EffectControlPoint? = null
                while (allEffect.isNotEmpty() && pos.toDouble() > (allEffect.peek()?.time ?: Double.MAX_VALUE))
                    ep = allEffect.poll()

                if (tp == null) tp = beatmap.controlPoints.timing.defaultControlPoint
                if (ep == null) ep = beatmap.controlPoints.effect.defaultControlPoint

                val finalTp = tp
                val finalEp = ep

                updateThread {
                    Verktex.withTreeLock {
                        // Discard stale result if the beatmap changed while we were parsing.
                        if (beatmapInfo != info) return@withTreeLock

                        timingControlPoints = allTiming; effectControlPoints = allEffect
                        currentTimingPoint = finalTp
                        currentEffectPoint  = finalEp
                        bpmLength  = finalTp?.msPerBeat?.toFloat() ?: 1000f

                        val nowKiai = finalEp?.isKiai == true
                        if (!kiaiActive && nowKiai) onKiaiStart()
                        else if (kiaiActive && !nowKiai) onKiaiEnd()
                        kiaiActive = nowKiai

                        // Update background for the new beatmap
                        if (info.audioPath != lastBgAudioPath) {
                            lastBgAudioPath = info.audioPath
                            crossfadeBackground(info)
                        }
                    }
                }
            } catch (e: IOException) {
                Log.w("MainMenuV2", "Failed to load timing points", e)
            } catch (e: NumberFormatException) {
                Log.w("MainMenuV2", "Failed to parse timing points for beatmap", e)
            } catch (e: IllegalArgumentException) {
                Log.w("MainMenuV2", "Failed to parse timing points for beatmap", e)
            }
        }
    }

    /** Load a random beatmap from the library and start playing it. */
    fun loadBeatmap() {
        LibraryManager.shuffleLibrary()
        if (LibraryManager.getSizeOfBeatmaps() == 0) return

        val info = LibraryManager.getCurrentBeatmapSet()[0]
        beatmapInfo = info
        GlobalManager.getInstance().selectedBeatmap = info

        // Load the background immediately reloadTimingPoints() is async and
        // may silently fail. gating the background
        // on it means a parse error leaves a blank/stale background forever.
        if (info.audioPath != lastBgAudioPath) {
            lastBgAudioPath = info.audioPath
            crossfadeBackground(info)
        }
        reloadTimingPoints()

        val svc = GlobalManager.getInstance().songService ?: return
        svc.preLoad(info.audioPath)
        musicStarted = false
    }

    /** Switch the active beatmap (e.g. after track change or replay launch). */
    fun setBeatmap(info: BeatmapInfo) {
        LibraryManager.findBeatmapSetIndex(info)
        beatmapInfo = info
        GlobalManager.getInstance().selectedBeatmap = info

        // Same reasoning as loadBeatmap(): load background immediately.
        if (info.audioPath != lastBgAudioPath) {
            lastBgAudioPath = info.audioPath
            crossfadeBackground(info)
        }
        reloadTimingPoints()
        musicControl(MusicOption.SYNC)
    }

    /** Handle PREV / PLAY / PAUSE / STOP / NEXT / SYNC music actions. */
    fun musicControl(option: MusicOption) {
        val songService = GlobalManager.getInstance().songService ?: return
        if (beatmapInfo == null) return

        when (option) {
            MusicOption.PREV -> {
                if (songService.status == Status.PLAYING || songService.status == Status.PAUSED)
                    songService.stop()

                LibraryManager.selectPreviousBeatmapSet()
                beatmapInfo = LibraryManager.getCurrentBeatmapSet()[0]
                GlobalManager.getInstance().selectedBeatmap = beatmapInfo
                reloadTimingPoints()
                songService.preLoad(beatmapInfo!!.audioPath)

                musicStarted = false
            }
            MusicOption.NEXT -> {
                if (songService.status == Status.PLAYING || songService.status == Status.PAUSED)
                    songService.stop()

                LibraryManager.selectNextBeatmapSet()
                beatmapInfo = LibraryManager.getCurrentBeatmapSet()[0]
                GlobalManager.getInstance().selectedBeatmap = beatmapInfo
                reloadTimingPoints()
                songService.preLoad(beatmapInfo!!.audioPath)

                musicStarted = false
            }
            MusicOption.PLAY -> {
                if (songService.status == Status.PAUSED || songService.status == Status.STOPPED) {
                    if (songService.status == Status.STOPPED) {
                        songService.preLoad(beatmapInfo!!.audioPath)
                    }
                    songService.play()
                }
            }
            MusicOption.PAUSE -> {
                if (songService.status == Status.PLAYING) songService.pause()
            }
            MusicOption.STOP -> {
                if (songService.status == Status.PLAYING || songService.status == Status.PAUSED)
                    songService.stop()
            }
            MusicOption.SYNC -> { /* timing already handled by reloadTimingPoints */ }
        }
    }

    /** Attach the online banner sprite to this scene. */
    fun loadBannerSprite() {
        if (!Config.isStayOnline()) return

        // Downloaded off the update thread, the textures are loaded on it.
        async {
            val downloaded = BannerManager.downloadBanners()

            updateThread {
                val sprite = BannerManager.loadBannerSprite(downloaded) ?: return@updateThread

                Verktex.withTreeLock {
                    bannerSprite = sprite
                    this@MainMenuV2 += sprite
                }
            }
        }
    }

    /**
     * One-time online setup: loads the online config, initialises OnlineManager,
     * creates the panel and kicks off login. Called once from onLoadComplete()
     * (legacy online panel setup).
     */
    fun initOnlinePanel() {
        Config.loadOnlineConfig(GlobalManager.getInstance().mainActivity)
        OnlineManager.getInstance().init()

        if (OnlineManager.getInstance().isStayOnline) {
            OnlineScoring.getInstance().createPanel()

            val panel = OnlineScoring.getInstance().panel
            panel.setPosition(5f, 5f)
            updateThread {
                Verktex.withTreeLock {
                    onlinePanel = panel
                    this += panel
                }
            }
        }

        OnlineScoring.getInstance().login()
    }

    /** Detach and recreate the online panel (called after settings change). */
    fun reloadOnlinePanel() {
        updateThread {
            Verktex.withTreeLock {
                onlinePanel?.let { this -= it }
                onlinePanel = null

                Config.loadOnlineConfig(GlobalManager.getInstance().mainActivity)
                OnlineManager.getInstance().init()

                if (OnlineManager.getInstance().isStayOnline) {
                    OnlineScoring.getInstance().createPanel()

                    val panel = OnlineScoring.getInstance().panel
                    panel.setPosition(5f, 5f)
                    onlinePanel = panel
                    this += panel
                }
            }

            OnlineScoring.getInstance().login()
        }
    }

    /** Show the exit confirmation dialog. */
    fun showExitDialog() {
        if (isOnExitAnim) return

        val dialog = object : UIConfirmDialog() {
            // Back does what the "No" button does: hide, which runs onCancel.
            override fun onInputEvent(event: InputEvent): Boolean {
                if (event is KeyboardEvent
                    && event.action == KeyboardEvent.Action.Down
                    && event.key == KeyboardEvent.Key.Back
                    && !staticBackdrop) {
                    hide()
                    return true
                }

                return super.onInputEvent(event)
            }
        }
        dialog.title = "Exit"
        dialog.text = GlobalManager.getInstance().mainActivity.getString(R.string.dialog_exit_message)
        dialog.onConfirm = { exit() }
        dialog.show()
    }

    /** Play exit animation and finish the activity. */
    fun exit() {
        if (isOnExitAnim) return

        isOnExitAnim = true

        menuOpen = false
        isSecondMenu = false

        // Buttons fade out
        for (btn in buttons) {
            btn.positionX.jumpTo(btn.positionX.value)
            btn.fade.animateTo(0f, 0.3f, Easing.OutQuad)
        }

        // Logo glides back to centre
        logoOffsetX.animateTo(0f, 0.8f, Easing.OutExpo)

        // Music player panel fades out
        panelAlpha.animateTo(0f, 0.5f, Easing.OutQuint)

        // Dim overlay fades out background brightens
        dimDipRemaining = 0f
        dimAlpha.animateTo(0f, 0.5f, Easing.OutSine)

        // Side flashes and star fountain disappear
        sideFlashes.fadeOut()
        starFountain.stop()

        // Re-raise the black overlay to the top of the draw stack so it covers
        // any elements that were dynamically attached after it (e.g. online panel,
        // banner), then fade it in over 3 seconds
        updateThread {
            Verktex.withTreeLock {
                fadeInOverlay.moveToFront()
                fadeInAlpha.jumpTo(0f)
                fadeInAlpha.animateTo(1f, 3.0f, Easing.None)
            }
        }

        // Audio
        val exitSound = ResourceManager.getInstance().getSound("seeya")
        exitSound?.play()

        GlobalManager.getInstance().songService?.stop()

        // Finish after the 3-second fade completes.
        // killOnDestroy = true ensures onDestroy() calls Process.killProcess(), fully
        // clearing all static singletons (ResourceManager, GlobalManager, UIEngine, etc.).
        // Without this, the process stays alive and on the next launch from the task switcher
        // the new Engine's FontManager is unaware of fonts still held in ResourceManager
        // (registered against the old Engine), causing black fonts and black icon squares.
        Handler(Looper.getMainLooper()).postDelayed({
            val activity = GlobalManager.getInstance().mainActivity
            activity.killOnDestroy = true
            activity.finish()
        }, 3000)
    }

    /** Load a replay file and jump straight to the scoring scene. */
    fun watchReplay(replayFile: String) {
        Log.d("MainMenuV2", "watchReplay: loading $replayFile")

        val replay = Replay()
        if (!replay.load(replayFile, false)) {
            Log.w("MainMenuV2", "watchReplay: failed to load replay file")
            ToastLogger.showText("Failed to load replay file.", true)
            return
        }
        if (replay.replayVersion < 3) {
            Log.w("MainMenuV2", "watchReplay: replay version ${replay.replayVersion} is too old (need >= 3)")
            ToastLogger.showText("Replay version is too old to watch.", true)
            return
        }

        val beatmap = LibraryManager.findBeatmapByMD5(replay.md5)
        if (beatmap == null) {
            Log.w("MainMenuV2", "watchReplay: beatmap with MD5 ${replay.md5} not found in library")
            ToastLogger.showText("The beatmap for this replay is not found in library.", true)
            return
        }

        setBeatmap(beatmap)

        val stat = replay.getStat()
        stat.migrateLegacyMods(beatmap.getBeatmapDifficulty())

        GlobalManager.getInstance().songMenu.select()
        ResourceManager.getInstance().loadBackground(beatmap.backgroundPath)

        val songService = GlobalManager.getInstance().songService!!
        songService.preLoad(beatmap.audioPath)
        songService.play()

        val scoring = GlobalManager.getInstance().scoring
        scoring.load(stat, null, songService, replayFile, null, beatmap)

        scoring.show()
        Log.d("MainMenuV2", "watchReplay: navigated to scoring scene")
    }

    /** Return to this scene (called from the song menu / LobbyScene on back). */
    fun show() {
        GlobalManager.getInstance().songService?.isGaming = false
        showSelf()

        val selected = GlobalManager.getInstance().selectedBeatmap
        if (selected != null)
            setBeatmap(selected)
    }

    /** Makes this the shown scene, onLoadComplete() runs on its next update. */
    private fun showSelf() {
        EngineRouter.showVerktex(this)
    }

    override fun onShown() {
        isLoadPending = true

        // Skin changes only reach the shown Verktex scene, so the regions are looked up again in
        // case the skin changed while this scene was not shown.
        propagate { (it as? ISkinnable)?.onSkinChanged() }

        // A release that went to AndEngine while this scene was not shown left its press behind.
        propagate { (it as? IPressable)?.cancelPress() }
    }

    companion object {
        /** Fraction of LOGO_SIZE that buttons overlap behind the logo edge. Tune this value. */
        private const val MENU_OVERLAP = 0.58f
    }

    // Expanding kiai ring
    // The centre and radius are in window pixels.
    private class ExpandingRing(cx: Float, cy: Float, r: Float) : UICircle() {
        private var progress = 0f

        init {
            width = (r * 2f).px; height = (r * 2f).px
            origin = Anchor.Center
            x = cx.px
            y = cy.px
            alpha = 0.5f
            color = Color4.White
        }
        override fun onUpdate(clock: TickingClock) {
            super.onUpdate(clock)
            progress += clock.elapsedTickTime / 0.8f

            if (progress >= 1f) {
                removeSelf()
                return
            }
            val s = 1f + 1.5f * progress
            scaleX = s; scaleY = s; alpha = (1f - progress) * 0.5f
        }
    }


    /**
     * A full-window background layer. [textureKey] is the key of a beatmap background it owns, which
     * is unloaded once the layer faded out.
     */
    private class BackgroundSprite(val textureKey: String?) : UISprite() {

        val fade = FloatAnimation(1f, 0f)

        private var isFadingOut = false

        init {
            width = fill()
            height = fill()
            scaleType = ScaleType.Crop
        }

        fun fadeOutAndRemove() {
            isFadingOut = true
            fade.animateTo(0f, 1.5f, Easing.OutCubic)
        }

        override fun onUpdate(clock: TickingClock) {
            alpha = fade.update(clock)

            if (isFadingOut && alpha <= 0f) {
                removeSelf()
                textureKey?.let(Textures::unload)
            }
        }
    }

    /**
     * A sprite tinted while pressed that calls [onRelease] when the press ends. With
     * [ignoreWhenHidden] it ignores touches while almost transparent.
     */
    private open class PressableSprite(
        private val ignoreWhenHidden: Boolean,
        private val onRelease: () -> Unit
    ) : UISprite(), IPressable {

        private var pressPointerId = -1

        private val isHidden get() = ignoreWhenHidden && alpha < 0.05f

        override fun onInputEvent(event: InputEvent): Boolean {
            if (event !is PointerEvent) return false

            if (event.action == PointerEvent.Action.Down) {
                if (pressPointerId != -1 || isHidden) return false

                pressPointerId = event.pointerId
                color = color.copy(red = 0.7f, green = 0.7f, blue = 0.7f)
                return true
            }

            if (event.pointerId != pressPointerId) return false

            when (event.action) {
                PointerEvent.Action.Up -> {
                    pressPointerId = -1
                    color = color.copy(red = 1f, green = 1f, blue = 1f)
                    // Released off the sprite: the press is cancelled, as AndEngine only reported releases on it.
                    if (!isHidden && containsScenePoint(event.x, event.y)) onRelease()
                    return true
                }
                PointerEvent.Action.Unknown -> {
                    // Cancel events are dispatched to every component, so they are not consumed.
                    cancelPress()
                    return false
                }
                else -> return true
            }
        }

        override fun cancelPress() {
            pressPointerId = -1
            color = color.copy(red = 1f, green = 1f, blue = 1f)
        }
    }

    /** One of the three menu buttons, sliding in from the logo. Positions are in virtual units. */
    private inner class MenuButton(onClick: () -> Unit) : PressableSprite(
        ignoreWhenHidden = true,
        onRelease = {
            playSound?.play()
            onClick()
        }
    ) {
        val positionX = FloatAnimation(0f, 0f)
        val fade = FloatAnimation(0f, 0f)
        var positionY = 0f

        init {
            alpha = 0f
        }

        override fun onUpdate(clock: TickingClock) {
            positionX.update(clock)
            alpha = fade.update(clock)
        }
    }
}

