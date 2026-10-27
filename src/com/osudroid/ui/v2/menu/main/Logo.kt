package com.osudroid.ui.v2.menu.main

import com.osudroid.ui.verktex.IPressable
import com.osudroid.ui.verktex.ISkinnable
import com.osudroid.ui.verktex.LegacyUnits.vu
import com.osudroid.ui.verktex.SkinTextures
import com.reco1l.verktex.data.Anchor
import com.reco1l.verktex.data.Color4
import com.reco1l.verktex.data.px
import com.reco1l.verktex.graphics.GraphicsTexture
import com.reco1l.verktex.input.InputEvent
import com.reco1l.verktex.input.PointerEvent
import com.reco1l.verktex.time.TickingClock
import com.reco1l.verktex.ui.UISprite
import com.reco1l.verktex.ui.container.UIContainer
import ru.nsu.ccfit.zuev.osu.Config
import ru.nsu.ccfit.zuev.osu.ToastLogger

class Logo(
    /** Called when the logo is tapped (short press). */
    val onTap: () -> Unit,
    /** Called when a kiai expand-circle burst should be spawned at the logo center, in window pixels. */
    val onKiaiBurst: ((centreX: Float, centreY: Float, radius: Float) -> Unit)? = null,
) : UIContainer(), ISkinnable, IPressable {


    /** Current BPM length in ms. Updated by the host scene every frame. */
    var bpmLength: Float = 1000f

    /** Whether kiai is currently active. Updated by the host scene. */
    var kiaiActive: Boolean = false
        set(value) {
            if (field == value) return
            field = value
            if (value) onKiaiStart() else onKiaiEnd()
        }


//    private val bgCircle: UICircle
    private val spectrum = Spectrum()
    private val halo: UISprite
    private val logoSprite: UISprite
    private val catJamSprite: KiaiCatJamSprite


    /** Smoothed beat energy (0–1). Updated every frame. */
    private var currentBeatVal = 0f

    /** Slow-moving average of raw FFT energy — used as the normalization baseline. */
    private var energyAvg = 0f

    /** Press-feedback scale (1.0 normally, dips on touch-down, springs on touch-up). */
    private var pressScale = 1f

    /** Extra scale injected on kiai start — decays back to 0. */
    private var kiaiPulse = 0f

    /**
     * Extra scale injected on each beat when FFT energy is not available
     * (e.g. music is paused or stopped).  Decays in [update] the same way
     * [kiaiPulse] does.  Set via [pulseBeat].
     */
    private var beatPulse = 0f
    private var catJamAlpha = 0f
    private var holdStartMs = -1L
    private var holdTriggered = false
    private var burstSent = false

    /** The finger pressing the logo, -1 while it is not pressed. */
    private var pressPointerId = -1

    /** Set when the skin regions must be looked up again on the next update. */
    private var texturesInvalid = true

    /** The catjam sheet the frames are cut from, and where it was in its texture then. */
    private var catJamSheet: GraphicsTexture.Region? = null
    private var catJamSheetX = 0f
    private var catJamSheetY = 0f

    /** Logo radius in pixels the spectrum was last configured for. */
    private var configuredRadius = -1f


    init {
        // Sized in performLayout(), the window size is not known yet.

        // 1. Pink background circle
//        bgCircle = UICircle().also { c ->
//            c.anchor = Anchor.Center
//            c.origin = Anchor.Center
//            c.color  = BG_COLOR
//            c.alpha  = 0.2f
//            this += c
//        }

        // 2. Spectrum
        this += spectrum

        // 3. Main logo sprite
        logoSprite = UISprite().also { s ->
            s.anchor = Anchor.Center
            s.origin = Anchor.Center
            this += s
        }

        //  4. Halo
        halo = UISprite().also { s ->
            s.anchor = Anchor.Center
            s.origin = Anchor.Center
            s.alpha = 0.2f
            this += s
        }

        // 5. CatJam, its frames are cut from the sheet on the first update.
        catJamSprite = KiaiCatJamSprite().also { cj ->
            cj.anchor = Anchor.Center
            cj.origin = Anchor.Center
            this += cj
        }
    }


    /**
     * Applies the virtual-unit sizes in window pixels. Called by the host scene's onPerformLayout().
     */
    fun performLayout() {
        val logoSize = LOGO_SIZE.vu
        val extra = Spectrum.MAX_EXTRA.vu

        width = (logoSize + extra * 2f).px
        height = (logoSize + extra * 2f).px

//        bgCircle.width  = (logoSize * 0.944f).px
//        bgCircle.height = (logoSize * 0.944f).px

        val radius = logoSize / 2f
        if (radius != configuredRadius) {
            configuredRadius = radius
            spectrum.configure(radius, extra)
        }

        logoSprite.width = logoSize.px
        logoSprite.height = logoSize.px

        halo.width = (logoSize * 1.07f).px
        halo.height = (logoSize * 1.07f).px

        catJamSprite.width = logoSize.px
        catJamSprite.height = logoSize.px
    }

    override fun onSkinChanged() {
        texturesInvalid = true
    }

    override fun onUpdate(clock: TickingClock) {
        if (texturesInvalid) {
            texturesInvalid = false

            val logoTex = SkinTextures.get("logo", "logo.png")
            logoSprite.textureRegion = logoTex
            halo.textureRegion = logoTex

            loadCatJam()
        }

        syncCatJamFrames()

        super.onUpdate(clock)
    }


    override fun onInputEvent(event: InputEvent): Boolean {
        if (event !is PointerEvent) return false

        if (event.action == PointerEvent.Action.Down) {
            if (pressPointerId != -1 || !isInsideDisc(event.x, event.y)) return false

            pressPointerId = event.pointerId
            holdStartMs = System.currentTimeMillis()
            holdTriggered = false
            pressScale = 0.93f
            return true
        }

        if (event.pointerId != pressPointerId) return false

        when (event.action) {
            PointerEvent.Action.Up -> {
                val triggered = holdTriggered
                pressPointerId = -1
                holdStartMs = -1L
                holdTriggered = false

                if (!isInsideDisc(event.x, event.y)) {
                    pressScale = 1f
                    return true
                }

                pressScale = 1.05f
                if (!triggered) onTap()
                return true
            }
            PointerEvent.Action.Unknown, PointerEvent.Action.Cancel -> {
                // Cancel events are dispatched to every component, so they are not consumed.
                cancelPress()
                return false
            }
            else -> return true
        }
    }

    override fun cancelPress() {
        pressPointerId = -1
        holdStartMs = -1L
        holdTriggered = false
        pressScale = 1f
    }

    private fun isInsideDisc(sceneX: Float, sceneY: Float): Boolean {
        val local = toLocalSpaceFromScene(sceneX, sceneY)
        val dx = local.x - bounds.width / 2f
        val dy = local.y - bounds.height / 2f
        val r = LOGO_SIZE.vu / 2f
        return dx * dx + dy * dy <= r * r
    }


    fun update(dt: Float, songPos: Int, bpmMs: Float, timingOffset: Int, fft: FloatArray?, isPlaying: Boolean) {
        this.bpmLength = bpmMs

        if (holdStartMs > 0 && !holdTriggered &&
            System.currentTimeMillis() - holdStartMs >= HOLD_TOGGLE_MS) {
            holdTriggered = true
            val enabled = !Config.isKiaiCatJam()
            Config.setKiaiCatJam(enabled)
            ToastLogger.showText(
                if (enabled) "🐱 CatJam: ON" else "🐱 CatJam: OFF",
                false
            )
        }

        // Beat detection via energy deviation from rolling average
        val rawEnergy = if (fft != null && fft.size >= 16) {
            var lowSum = 0f
            for (i in 1..3)  lowSum  += fft[i]
            val lows = lowSum / 3f

            var midSum = 0f
            for (i in 4..7)  midSum  += fft[i]
            val mids = midSum / 4f

            var vocalSum = 0f
            for (i in 8..12) vocalSum += fft[i]
            val vocals = vocalSum / 5f

            mids + lows * 0.5f + vocals * 0.25f
        } else 0f

        // Slow average adapts to the song's overall level (~650 ms window).
        energyAvg = lerp(energyAvg, rawEnergy, dt * 1.5f)

        // Beat = ratio of current energy above the average (0 between beats, 0.5–1 on hits).
        val rawBeat = ((rawEnergy / (energyAvg + 0.0001f) - 1f) * 3f).coerceIn(0f, 1f)
        currentBeatVal = lerp(currentBeatVal, rawBeat, dt * 20f)

        // Scale animation
        kiaiPulse = lerp(kiaiPulse,  0f, dt * 8f)
        beatPulse = lerp(beatPulse,  0f, dt * 8f)
        pressScale = lerp(pressScale, 1f, dt * 20f)

        val logoS = (1f + 0.25f * currentBeatVal + kiaiPulse + beatPulse) * pressScale
        val haloS = logoS
//        val bgS =  1f + 0.12f * currentBeatVal + kiaiPulse

        logoSprite.scale = logoS
        halo.scale = haloS
//        bgCircle.scale = bgS

        // Spectrum
        spectrum.update(dt, kiaiActive)

        // CatJam
        val showCat = Config.isKiaiCatJam() && kiaiActive && isPlaying
        catJamAlpha = if (showCat)
            (catJamAlpha + dt * 5f).coerceAtMost(1f)
        else
            (catJamAlpha - dt * 10f).coerceAtLeast(0f)

        catJamSprite.alpha = catJamAlpha * 0.5f

        if (catJamAlpha > 0f) {
            updateCatJamFrame(songPos, bpmMs, timingOffset)
        }
    }


    private fun onKiaiStart() {
        kiaiPulse = 0.15f

        if (!burstSent) {
            burstSent = true
            val (cx, cy) = toSceneSpace(bounds.width / 2f, bounds.height / 2f)
            onKiaiBurst?.invoke(cx, cy, LOGO_SIZE.vu / 2f)
        }
    }

    private fun onKiaiEnd() {
        burstSent = false
    }

    /** Called by the host scene on each detected BPM beat to boost the spectrum ring spin. */
    fun onBeat() = spectrum.onBeat(kiaiActive)

    /**
     * Fire an explicit scale pulse — used when music is paused or stopped so the
     * logo keeps visually bouncing at the BPM even with no live FFT data.
     */
    fun pulseBeat() {
        beatPulse = 0.07f
        spectrum.onBeat(kiaiActive)
    }


    private fun updateCatJamFrame(songPos: Int, bpmMs: Float, timingOffset: Int) {
        val cj = catJamSprite
        val frames = cj.frames
        val total  = frames.size
        if (total <= 0 || bpmMs <= 0f) return

        val totalBeats = (songPos - timingOffset).toFloat() / bpmMs
        val imagesPerBeat = total / 13f
        val input = totalBeats * imagesPerBeat
        val period = total * 2f
        val wrapped = ((input % period) + period) % period
        val frameF = if (wrapped <= total) wrapped else (period - wrapped)
        val frameIdx = frameF.toInt().coerceIn(0, total - 1)

        cj.showFrame(frameIdx)
    }


    /**
     * Cuts the frames out of the catjam sheet, a 13 column grid of 112 px frames. Must run on the
     * update thread.
     */
    private fun loadCatJam() {
        val sheet = SkinTextures.get("catjam-spritesheet", "catjam-spritesheet.png")
        catJamSheet = sheet

        if (sheet == null) {
            catJamSprite.frames = emptyList()
            return
        }

        val frameSize = KiaiCatJamSprite.FRAME_SIZE.toFloat()
        val cols = KiaiCatJamSprite.GRID_COLS

        catJamSprite.frames = List(KiaiCatJamSprite.TOTAL_FRAMES) { idx ->
            GraphicsTexture.Region(
                sheet.texture, sheet.source,
                sheet.x + idx % cols * frameSize, sheet.y + idx / cols * frameSize,
                frameSize, frameSize
            )
        }
        catJamSheetX = sheet.x
        catJamSheetY = sheet.y
    }

    /**
     * Moves the frames along when the sheet was repacked within its atlas, which happens whenever
     * another region of that atlas is unloaded.
     */
    private fun syncCatJamFrames() {
        val sheet = catJamSheet ?: return
        if (sheet.x == catJamSheetX && sheet.y == catJamSheetY) return

        val dx = sheet.x - catJamSheetX
        val dy = sheet.y - catJamSheetY
        for (frame in catJamSprite.frames) {
            frame.x += dx
            frame.y += dy
        }
        catJamSheetX = sheet.x
        catJamSheetY = sheet.y
    }


    private fun lerp(a: Float, b: Float, t: Float) = a + (b - a) * t.coerceIn(0f, 1f)


    companion object {
        /** Base size of the logo disc in virtual units, converted to pixels with [vu]. */
        val LOGO_SIZE get() = Config.getRES_HEIGHT() * 0.7f

        private const val HOLD_TOGGLE_MS = 3_000L

        // pink sampled from logo.png
        @Suppress("unused")
        private val BG_COLOR = Color4(233 / 255f, 103 / 255f, 161 / 255f, 1.0f)
    }
}
