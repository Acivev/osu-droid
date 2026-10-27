package com.osudroid.ui.v2.menu.main

import com.osudroid.ui.verktex.toVerktex
import com.reco1l.verktex.data.Anchor
import com.reco1l.verktex.data.Color4
import com.reco1l.verktex.data.fill
import com.reco1l.verktex.graphics.BlendMode
import com.reco1l.verktex.math.Easing
import com.reco1l.verktex.math.FloatAnimation
import com.reco1l.verktex.time.TickingClock
import com.reco1l.verktex.ui.UIGradientBox
import com.reco1l.verktex.ui.UIScene
import ru.nsu.ccfit.zuev.skins.OsuSkin

/**
 * Behavior matches lazer Flashes:
 *  - Outside kiai:  both sides flash together on the measure downbeat
 *  - During kiai:   left on even beats, right on odd beats (alternating)
 *  - Color:         skin's first combo color (skips white/black), fallback = blue
 *  - Alpha:         amplitude-driven from FFT energy
 *  - Animation:     65 ms fade-in → beat-length Easing.In fade-out
 *
 * Usage:
 * 1. Create:   `val sideFlashes = SideFlashes()`
 * 2. Attach:   `sideFlashes.attachTo(scene)`
 * 3. Per beat: `sideFlashes.onNewBeat(beat, kiaiActive, bpmMs, fft, timeSignature)`
 *
 * Each flash is 15% of the window width.
 */
class SideFlashes {

    private val flashLeft = Flash(fromAlpha = 1f, toAlpha = 0f)
    private val flashRight = Flash(fromAlpha = 0f, toAlpha = 1f)

    init {
        flashLeft.apply {
            width = fill(FLASH_WIDTH)
            height = fill()
            alpha = 0f
            flashColor = DEFAULT_COLOR
        }
        flashRight.apply {
            width = fill(FLASH_WIDTH)
            height = fill()
            anchor = Anchor.TopRight
            origin = Anchor.TopRight
            alpha = 0f
            flashColor = DEFAULT_COLOR
        }
    }

    /** Attach both flash boxes to [scene]. Call once during scene init. */
    fun attachTo(scene: UIScene) {
        scene += flashLeft
        scene += flashRight
    }

    /**
     * Drive the flash on each new beat.
     *
     * @param beat          current beat index (songPos / msPerBeat)
     * @param kiaiActive    whether kiai is currently active
     * @param bpmMs         current beat length in milliseconds
     * @param fft           raw FFT spectrum from the audio service (maybe null)
     * @param timeSignature beats per measure (used for downbeat detection outside kiai)
     */
    fun onNewBeat(
        beat: Int,
        kiaiActive: Boolean,
        bpmMs: Float,
        fft: FloatArray?,
        timeSignature: Int
    ) {
        // Amplitude-based alpha
        val energy = if (fft != null && fft.size > 8) {
            var sum = 0f
            for (i in 1..8) sum += fft[i]
            (sum / 8f * FFT_SCALE).coerceIn(0f, 1f)

        } else 0.5f

        val deadZone = 0.25f
        val multiplier =
            if (kiaiActive) (1f - deadZone * 0.95f) / 0.8f
            else (1f - deadZone) / 0.55f

        val targetAlpha = (0.1f + (energy - deadZone) / multiplier).coerceIn(0.1f, 1f)
        val beatLenSec  = bpmMs / 1000f

        // Outside kiai: both flash on measure downbeat
        // During kiai:  alternate L/R every beat
        val flashL =
            if (kiaiActive) beat % 2 == 0
            else beat % timeSignature == 0
        val flashR =
            if (kiaiActive) beat % 2 == 1
            else beat % timeSignature == 0

        if (flashL) trigger(flashLeft,  targetAlpha, beatLenSec)
        if (flashR) trigger(flashRight, targetAlpha, beatLenSec)
    }


    private fun trigger(box: Flash, targetAlpha: Float, beatLenSec: Float) {
        box.flashColor = resolveColor()
        box.fadeTo(targetAlpha, FADE_IN_SEC, Easing.None)
        box.queueFadeOut(beatLenSec)
    }

    /** Skin's first combo color, skipping near-white and near-black. Falls back to [DEFAULT_COLOR]. */
    private fun resolveColor(): Color4 {
        return OsuSkin.get().getComboColor().firstOrNull { c ->
            val isNearWhite = c.red > 0.9f && c.green > 0.9f && c.blue > 0.9f
            val isNearBlack = c.red < 0.1f && c.green < 0.1f && c.blue < 0.1f
            !isNearWhite && !isNearBlack
        }?.toVerktex() ?: DEFAULT_COLOR
    }

    /** Immediately stop any in-flight animations and hide both flash boxes. */
    fun fadeOut(duration: Float = 0.064f, easing: Easing = Easing.OutQuint) {
        flashLeft.fadeTo(0f, duration, easing)
        flashRight.fadeTo(0f, duration, easing)
    }


    /**
     * One additive gradient flash. Its alpha runs the fade-in then the fade-out queued after it, as the
     * chained modifiers of the AndEngine version did.
     */
    private class Flash(private val fromAlpha: Float, private val toAlpha: Float) : UIGradientBox() {

        private val fade = FloatAnimation(0f, 0f)

        /** Seconds of the fade-in left before the queued fade-out starts, -1 when none is queued. */
        private var fadeInRemaining = -1f

        /** Duration of the queued fade-out. */
        private var queuedFadeOut = 0f

        /** The flash colour, its alpha is replaced by the gradient's. */
        var flashColor: Color4 = DEFAULT_COLOR
            set(value) {
                field = value
                colorStart = value.copy(alpha = fromAlpha)
                colorEnd = value.copy(alpha = toAlpha)
            }

        init {
            gradientAngle = 0f
            blendMode = BlendMode.Additive
        }

        /** Replaces any running or queued fade, like clearEntityModifiers() followed by fadeTo(). */
        fun fadeTo(target: Float, duration: Float, easing: Easing) {
            fadeInRemaining = -1f
            if (duration <= 0f) {
                fade.jumpTo(target)
            } else {
                fade.jumpTo(fade.value)
                fade.animateTo(target, duration, easing)
            }
        }

        /** Fades out over [duration] once the running fade-in is done, as `.after { fadeTo(0f, …) }` did. */
        fun queueFadeOut(duration: Float) {
            fadeInRemaining = FADE_IN_SEC
            queuedFadeOut = duration
        }

        override fun onUpdate(clock: TickingClock) {
            alpha = fade.update(clock)

            // The fade-in advanced by the same time, so it has reached its target here.
            if (fadeInRemaining >= 0f) {
                fadeInRemaining -= clock.elapsedTickTime
                if (fadeInRemaining <= 0f) fadeTo(0f, queuedFadeOut, Easing.InQuad)
            }
        }
    }


    companion object {
        /** Default blue*/
        val DEFAULT_COLOR = Color4(0.4f, 0.8f, 1f, 1f)

        /** Scale applied to raw FFT values when computing flash amplitude. */
        private const val FFT_SCALE = 8f

        /** Width of each flash as a fraction of the window width. */
        private const val FLASH_WIDTH = 0.15f

        private const val FADE_IN_SEC = 0.065f
    }
}

