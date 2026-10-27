package com.osudroid.ui.v2.menu.main

import com.osudroid.ui.verktex.ISkinnable
import com.osudroid.ui.verktex.LegacyUnits
import com.osudroid.ui.verktex.LegacyUnits.vu
import com.osudroid.ui.verktex.SkinTextures
import com.reco1l.verktex.data.Anchor
import com.reco1l.verktex.data.Axis
import com.reco1l.verktex.data.ClipMode
import com.reco1l.verktex.data.Color4
import com.reco1l.verktex.data.LayoutConstraints
import com.reco1l.verktex.data.Padding
import com.reco1l.verktex.data.Vec2
import com.reco1l.verktex.data.fill
import com.reco1l.verktex.data.px
import com.reco1l.verktex.data.wrap
import com.reco1l.verktex.graphics.GraphicsTexture
import com.reco1l.verktex.math.Easing
import com.reco1l.verktex.math.FloatAnimation
import com.reco1l.verktex.time.TickingClock
import com.reco1l.verktex.ui.UIButton
import com.reco1l.verktex.ui.UIComponent
import com.reco1l.verktex.ui.UISprite
import com.reco1l.verktex.ui.container.UIClickableContainer
import com.reco1l.verktex.ui.container.UIContainer
import com.reco1l.verktex.ui.container.UILinearContainer
import com.reco1l.verktex.ui.container.container
import com.reco1l.verktex.ui.control.UISlider
import com.reco1l.verktex.ui.shape.UIBox
import com.reco1l.verktex.ui.shape.UITriangle
import com.reco1l.verktex.ui.text.UIText
import ru.nsu.ccfit.zuev.audio.Status
import ru.nsu.ccfit.zuev.osu.GlobalManager

/**
 * Collapsible now-playing panel, top-right.
 *
 * Collapsed → header only: song title + thin read-only progress strip.
 * Expanded  → header + full interactive slider + time labels + prev/play/next.
 *
 * Sizes are virtual units, applied in window pixels by [performLayout].
 */
class MusicPlayerPanel : UILinearContainer(), ISkinnable {

    var isDraggingSeek = false
        private set
    private var isExpanded = false

    /**
     * Cached texture refs, looked up on the update thread, reused every frame.
     */
    private var pauseTex: GraphicsTexture.Region? = null
    private var playTex2: GraphicsTexture.Region? = null

    /** Set when the skin regions must be looked up again on the next update. */
    private var texturesInvalid = true

    /**
     * Last-seen values, updated only when something changes, preventing
     * redundant string allocations and texture lookups every frame.
     */
    private var lastTitle = ""
    private var lastStatus: Status? = null

    // Cached last-rendered seconds to avoid per-frame string allocations when the
    // displayed time hasn't actually changed (formatTime result is the same for every
    // ms within the same second).
    private var lastPosSec = -1
    private var lastLenSec = -1
    private var lastSeekSec = -1

    private val autoCloseDelayMs = 5_000L
    private var lastInteractionTime = 0L

    /** Called after PREV or NEXT is triggered so the parent scene can reset its music state. */
    var onTrackChanged: (() -> Unit)? = null

    /** Called after the user seeks to a new position so the parent scene can resync timing. */
    var onSeek: (() -> Unit)? = null

    // Animations, in virtual units
    private val panelWidth = FloatAnimation(COLLAPSED_WIDTH, 0.15f, Easing.None)
    private val arrowRotation = FloatAnimation(180f, 0.15f, Easing.None)  // starts collapsed → pointing up
    private val expandProgress = FloatAnimation(0f, 0.15f, Easing.None)

    // Mini progress fill
    private val miniProgressFill = UIBox().apply {
        width = fill(0f)
        height = fill()
        backgroundColor = Color4.White
    }

    private val miniProgressStrip = UIContainer().apply {
        width = fill()
        +miniProgressFill
    }

    private val seekSlider = UISlider(0f).apply {
        width = fill()
        min = 0f
        max = 1f
        onStartDragging = { isDraggingSeek = true }
        onValueChange = { value ->
            if (isDraggingSeek) {
                GlobalManager.getInstance().songService?.let { svc ->
                    svc.seekTo((value * svc.getLength()).toInt())
                }
            }
        }
    }

    private val posText = UIText().apply {
        color = Color4.White
        wrapText = false
        text = "0:00"
    }

    private val totalText = UIText().apply {
        color = Color4.White
        wrapText = false
        anchor = Anchor.TopRight
        origin = Anchor.TopRight
        text = "0:00"
    }

    private val prevBtn = MusicButton().apply {
        onActionUp = {
            lastInteractionTime = System.currentTimeMillis()
            GlobalManager.getInstance().mainMenuV2?.musicControl(MusicOption.PREV)
            onTrackChanged?.invoke()
        }
    }

    private val playPauseBtn = MusicButton().apply {
        onActionUp = {
            lastInteractionTime = System.currentTimeMillis()
            val svc = GlobalManager.getInstance().songService
            if (svc != null) {
                if (svc.status == Status.PLAYING)
                    GlobalManager.getInstance().mainMenuV2?.musicControl(MusicOption.PAUSE)
                else
                    GlobalManager.getInstance().mainMenuV2?.musicControl(MusicOption.PLAY)
            }
        }
    }

    private val nextBtn = MusicButton().apply {
        onActionUp = {
            lastInteractionTime = System.currentTimeMillis()
            GlobalManager.getInstance().mainMenuV2?.musicControl(MusicOption.NEXT)
            onTrackChanged?.invoke()
        }
    }

    private val songTitleText = UIText().apply {
        color = Color4.White
        wrapText = false
        clipping = ClipMode.Bounds
        autoScrollAxes = Axis.X
        text = ""
    }

    private val arrowTriangle = UITriangle().apply {
        anchor = Anchor.CenterRight
        origin = Anchor.CenterRight
        rotationOrigin = Anchor.Center
        color = Color4.White
    }

    private val header: UIClickableContainer
    private val titleColumn: UILinearContainer
    private val controlsRow: UILinearContainer
    private val expandedContent: UILinearContainer
    private val expandedSection: ExpandingSection

    init {
        seekSlider.onStopDragging = {
            val seekTarget = seekSlider.value

            isDraggingSeek = false
            lastInteractionTime = System.currentTimeMillis()

            val svc = GlobalManager.getInstance().songService
            if (svc != null) {
                svc.seekTo((seekTarget * svc.getLength()).toInt())
                onSeek?.invoke()
            }
        }

        anchor = Anchor.TopRight
        origin = Anchor.TopRight
        orientation = Orientation.Vertical
        height = wrap()
        backgroundColor = Color4(0.1f, 0.12f, 0.16f, 0.88f)

        // Header
        titleColumn = UILinearContainer().apply {
            orientation = Orientation.Vertical
            width = fill()

            +songTitleText
            +miniProgressStrip
        }

        header = UIClickableContainer().apply {
            width = fill()
            onActionUp = { lastInteractionTime = System.currentTimeMillis(); toggle() }

            +titleColumn
            +arrowTriangle
        }
        +header

        // Expanded section
        controlsRow = UILinearContainer().apply {
            anchor = Anchor.TopCenter
            origin = Anchor.TopCenter

            +prevBtn
            +playPauseBtn
            +nextBtn
        }

        expandedContent = UILinearContainer().apply {
            orientation = Orientation.Vertical
            width = fill()

            +seekSlider

            // Time labels row
            container {
                width = fill()
                +posText
                +totalText
            }

            // Controls row, centred
            container {
                width = fill()
                +controlsRow
            }
        }

        expandedSection = ExpandingSection(expandedContent).apply {
            width = fill()
            clipping = ClipMode.Bounds
            isVisible = false
        }
        +expandedSection
    }


    /**
     * Applies the virtual-unit sizes in window pixels. Called by the host scene's onPerformLayout().
     */
    fun performLayout() {
        val widthVu = panelWidth.value

        width = widthVu.vu.px
        x = (-10f).vu.px
        y = 10f.vu.px
        radius = 20f.vu.px

        header.padding = Padding(left = 16f.vu.px, top = 12f.vu.px, right = 16f.vu.px, bottom = 8f.vu.px)

        // Keep title from overlapping the arrow as the panel width animates
        songTitleText.fontSize = LegacyUnits.fontSize("font").vu.px
        songTitleText.width = (widthVu - 16f - 16f - ARROW_WIDTH - 16f).coerceAtLeast(0f).vu.px
        songTitleText.autoScrollSpeed = 25f.vu

        arrowTriangle.width = ARROW_WIDTH.vu.px
        arrowTriangle.height = 10f.vu.px
        arrowTriangle.rotationZ = arrowRotation.value

        titleColumn.spacing = 4f.vu.px
        miniProgressStrip.height = 4f.vu.px
        miniProgressStrip.padding = Padding(right = 12f.vu.px)
        miniProgressFill.radius = 2f.vu.px

        expandedContent.spacing = 10f.vu.px
        expandedContent.padding = Padding(left = 16f.vu.px, right = 16f.vu.px, bottom = 12f.vu.px)
        expandedSection.progress = expandProgress.value

        seekSlider.height = 48f.vu.px

        val smallFont = LegacyUnits.fontSize("smallFont").vu.px
        posText.fontSize = smallFont
        totalText.fontSize = smallFont

        controlsRow.spacing = 12f.vu.px
        prevBtn.performLayout()
        playPauseBtn.performLayout()
        nextBtn.performLayout()
    }

    override fun onSkinChanged() {
        texturesInvalid = true
    }

    override fun onUpdate(clock: TickingClock) {
        if (texturesInvalid) {
            texturesInvalid = false

            pauseTex = SkinTextures.get("music_pause", "music_pause.png")
            playTex2 = SkinTextures.get("music_play", "music_play.png")
            prevBtn.icon = SkinTextures.get("music_prev", "music_prev.png")
            nextBtn.icon = SkinTextures.get("music_next", "music_next.png")

            // Looked up again on the next update().
            lastStatus = null
            playPauseBtn.icon = playTex2
        }

        panelWidth.update(clock)
        arrowRotation.update(clock)
        val progress = expandProgress.update(clock)

        // The collapse animation finished.
        if (!isExpanded && progress <= 0f) expandedSection.isVisible = false

        super.onUpdate(clock)
    }


    fun collapse() {
        if (isExpanded) toggle()
    }

    private fun toggle() {
        if (isExpanded) {
            isExpanded = false
            arrowRotation.animateTo(180f, 0.15f, Easing.None)
            panelWidth.animateTo(COLLAPSED_WIDTH, 0.15f, Easing.None)
            expandProgress.animateTo(0f, 0.15f, Easing.None)
        } else {
            isExpanded = true
            arrowRotation.animateTo(0f, 0.15f, Easing.None)
            panelWidth.animateTo(EXPANDED_WIDTH, 0.15f, Easing.None)
            expandedSection.isVisible = true
            expandProgress.animateTo(1f, 0.15f, Easing.None)
        }
    }

    fun update() {
        val songService = GlobalManager.getInstance().songService ?: return
        val info = GlobalManager.getInstance().mainMenuV2?.beatmapInfo

        val title = if (info != null) "${info.artistText} - ${info.titleText}" else ""
        if (title != lastTitle) {
            lastTitle = title
            songTitleText.text = title
        }

        val pos = songService.position
        val len = songService.getLength()
        val progress = if (len > 0) (pos.toFloat() / len.toFloat()).coerceIn(0f, 1f) else 0f

        miniProgressFill.width = fill(progress)

        if (isExpanded) {
            if (lastInteractionTime > 0 &&
                System.currentTimeMillis() - lastInteractionTime >= autoCloseDelayMs &&
                !isDraggingSeek) {
                collapse()
            }

            val lenSec = len / 1000
            if (lenSec != lastLenSec) {
                lastLenSec = lenSec
                totalText.text = formatTime(len)
            }

            if (!isDraggingSeek) {
                seekSlider.value = progress
                val posSec = pos / 1000
                if (posSec != lastPosSec) {
                    lastPosSec = posSec
                    posText.text = formatTime(pos)
                }
            } else {
                val seekSec = (seekSlider.value * len).toInt() / 1000
                if (seekSec != lastSeekSec) {
                    lastSeekSec = seekSec
                    posText.text = formatTime((seekSlider.value * len).toInt())
                }
            }

            val status = songService.status
            if (status != lastStatus) {
                lastStatus = status
                playPauseBtn.icon = if (status == Status.PLAYING) pauseTex else playTex2
            }
        }
    }

    private fun formatTime(ms: Int): String {
        val s = ms / 1000
        return "%d:%02d".format(s / 60, s % 60)
    }


    /**
     * Shows [content] at its full height times [progress], clipping the rest, so the section can
     * open and close without the content being squashed.
     */
    private class ExpandingSection(private val content: UIComponent) : UIContainer() {

        var progress = 0f
            set(value) {
                if (field == value) return
                field = value
                invalidateMeasure()
            }

        init {
            this += content
        }

        override fun onMeasure(parentConstraints: LayoutConstraints, contentConstraints: LayoutConstraints): Vec2 {
            val contentSize = content.measure(contentConstraints.loosen().copy(maxHeight = Float.POSITIVE_INFINITY))
            return parentConstraints.constrain(Vec2(contentSize.x, contentSize.y * progress))
        }
    }

    /** A button showing a skin texture as its icon, as the AndEngine UIIconButton did. */
    private class MusicButton : UIButton() {

        private val sprite = UISprite().apply {
            scaleType = UISprite.ScaleType.Fit
            anchor = Anchor.Center
            origin = Anchor.Center
            // The AndEngine button tinted its icon with the theme accent.
            style = Style { theme -> color = theme.palette.accent }
        }

        var icon by sprite::textureRegion

        init {
            // Only the colour comes from the theme, the sizes are virtual units.
            style = Style { theme -> backgroundColor = theme.palette.buttonBackgroundSurface }
            width = wrap()
            height = wrap()

            +sprite
        }

        fun performLayout() {
            sprite.width = 28f.vu.px
            sprite.height = 28f.vu.px
            padding = Padding(horizontal = 12f.vu.px, vertical = 16f.vu.px)
            radius = 12f.vu.px
        }
    }


    companion object {
        private const val COLLAPSED_WIDTH = 550f
        private const val EXPANDED_WIDTH = 450f
        private const val ARROW_WIDTH = 14f
    }
}
