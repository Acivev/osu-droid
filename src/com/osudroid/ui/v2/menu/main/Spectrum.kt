package com.osudroid.ui.v2.menu.main

import com.reco1l.verktex.BuiltIn
import com.reco1l.verktex.data.Anchor
import com.reco1l.verktex.data.px
import com.reco1l.verktex.graphics.BlendMode
import com.reco1l.verktex.graphics.GraphicsRenderer
import com.reco1l.verktex.graphics.GraphicsShader
import com.reco1l.verktex.ui.UICanvas
import ru.nsu.ccfit.zuev.osu.Config
import ru.nsu.ccfit.zuev.osu.GlobalManager
import kotlin.math.*

/**
 * Audio-reactive spectrum visualizer
 *
 * Quality levels (set via Settings → Graphics → Spectrum visualizer):
 *  - "full" : 3 rounds × 120 bars, FFT every 50 ms  — full visual fidelity.
 *  - "low"  : 1 round  × 120 bars, FFT every 100 ms — ~66 % less geometry, half the FFT reads.
 *  - "off"  : hidden, nothing is drawn.
 *
 * Every drawn frame the bars of the active rounds that are above the dead zone are added as two
 * triangles each to a single additive draw command.
 */
class Spectrum : UICanvas() {

    private val frequencyAmplitudes = FloatArray(BAR_COUNT)
    private val temporalAmplitudes = FloatArray(BAR_COUNT)
    private var indexOffset = 0
    private var timeSinceLastAmpUpdate = 0f

    private var barStartX = FloatArray(0)
    private var barStartY = FloatArray(0)
    private var barCosA = FloatArray(0)
    private var barSinA = FloatArray(0)
    private var halfBarH = 0f
    private var maxBarLen = 0f
    private var totalBars = 0

    private var activeRounds = VISUALISER_ROUNDS
    private var updateInterval = TIME_BETWEEN_UPDATES_FULL


    init {
        // Centred in the logo: the logo's own bounds are clamped to the window, the bars overflow them.
        anchor = Anchor.Center
        origin = Anchor.Center
        blendMode = BlendMode.Additive
        alpha = BAR_ALPHA
    }


    /**
     * Places the bars around the centre of this component. Both values are in window pixels,
     * [maxExtra] is [MAX_EXTRA] converted to pixels.
     */
    fun configure(logoRadius: Float, maxExtra: Float) {
        maxBarLen = BAR_LENGTH * logoRadius
        halfBarH  = (logoRadius * 2f *
                sqrt(2.0 * (1.0 - cos(2.0 * PI / BAR_COUNT)))).toFloat() / 4f

        totalBars = BAR_COUNT * VISUALISER_ROUNDS
        barStartX = FloatArray(totalBars)
        barStartY = FloatArray(totalBars)
        barCosA = FloatArray(totalBars)
        barSinA = FloatArray(totalBars)

        for (k in 0 until VISUALISER_ROUNDS) {
            val roundOffset = k * 360.0 / VISUALISER_ROUNDS
            for (i in 0 until BAR_COUNT) {
                val idx = k * BAR_COUNT + i
                val angleRad = Math.toRadians(i.toDouble() / BAR_COUNT * 360.0 + roundOffset)
                val ca = cos(angleRad).toFloat()
                val sa = sin(angleRad).toFloat()
                val startR = logoRadius * BAR_START_INSET

                barStartX[idx] = startR * ca
                barStartY[idx] = startR * sa
                barCosA[idx] = ca
                barSinA[idx] = sa
            }
        }

        width = (logoRadius * 2f + maxExtra * 2f).px
        height = (logoRadius * 2f + maxExtra * 2f).px
    }

    override fun onDraw(renderer: GraphicsRenderer) {
        val activeBars = min(BAR_COUNT * activeRounds, totalBars)

        renderer.setState(
            shader = BuiltIn.Shaders.SolidQuad,
            texture = null,
            blendMode = BlendMode.Additive,
            uniformSet = GraphicsShader.UniformSet()
        )

        renderer.drawGeometry { transform, _ ->
            val color = renderer.transformColor(contentColor)
            val hh = halfBarH
            val ml = maxBarLen
            val centreX = bounds.width / 2f
            val centreY = bounds.height / 2f

            for (total in 0 until activeBars) {
                val amp = frequencyAmplitudes[total % BAR_COUNT]
                if (amp < AMPLITUDE_DEAD_ZONE) continue

                val len = amp * ml
                val sx = centreX + barStartX[total]
                val sy = centreY + barStartY[total]
                val ca = barCosA[total]
                val sa = barSinA[total]

                val (ax, ay) = transform.transform(sx - sa * hh, sy + ca * hh)
                val (bx, by) = transform.transform(sx + sa * hh, sy - ca * hh)
                val (cx, cy) = transform.transform(sx + ca * len + sa * hh, sy + sa * len - ca * hh)
                val (dx, dy) = transform.transform(sx + ca * len - sa * hh, sy + sa * len + ca * hh)

                // The quad shader fades out at UV edges, so every vertex samples its centre.
                with(renderer) {
                    addVertex(ax, ay, 0.5f, 0.5f, color)
                    addVertex(bx, by, 0.5f, 0.5f, color)
                    addVertex(cx, cy, 0.5f, 0.5f, color)
                    addVertex(ax, ay, 0.5f, 0.5f, color)
                    addVertex(cx, cy, 0.5f, 0.5f, color)
                    addVertex(dx, dy, 0.5f, 0.5f, color)
                }
            }
        }
    }

    fun update(dt: Float, kiaiActive: Boolean) {
        when (Config.getSpectrumQuality()) {
            "off" -> {
                isVisible = false
                frequencyAmplitudes.fill(0f)
                return
            }
            "low" -> {
                isVisible = true
                activeRounds = VISUALISER_ROUNDS_LOW
                updateInterval = TIME_BETWEEN_UPDATES_LOW
            }
            else  -> { // "full"
                isVisible = true
                activeRounds = VISUALISER_ROUNDS
                updateInterval = TIME_BETWEEN_UPDATES_FULL
            }
        }

        // Non-linear decay
        val decayFactor = dt * 1000f * DECAY_PER_MILLISECOND
        for (i in 0 until BAR_COUNT) {
            frequencyAmplitudes[i] -= decayFactor * (frequencyAmplitudes[i] + 0.03f)
            if (frequencyAmplitudes[i] < 0f) frequencyAmplitudes[i] = 0f
        }

        // Throttled FFT read
        timeSinceLastAmpUpdate += dt
        if (timeSinceLastAmpUpdate >= updateInterval) {
            timeSinceLastAmpUpdate -= updateInterval
            updateAmplitudes(kiaiActive)
        }
    }

    fun onBeat(@Suppress("UNUSED_PARAMETER") kiaiActive: Boolean) {
        indexOffset = (indexOffset + BEAT_INDEX_JUMP) % BAR_COUNT
    }

    private fun updateAmplitudes(kiaiActive: Boolean) {
        val fft = GlobalManager.getInstance().songService?.spectrum
        for (i in 0 until BAR_COUNT) temporalAmplitudes[i] = 0f

        if (fft != null) {
            val windowSize = 240
            var leftBound  = 0

            for (i in 0 until BAR_COUNT) {
                var peak = 0f
                var rightBound = 2.0.pow(i * 9.0 / (windowSize - 1)).toInt()

                if (rightBound <= leftBound)
                    rightBound = leftBound + 1

                if (rightBound > 511)
                    rightBound = 511

                while (leftBound < rightBound) {
                    if (leftBound + 1 < fft.size && fft[1 + leftBound] > peak)
                        peak = fft[1 + leftBound]
                    leftBound++
                }
                temporalAmplitudes[i] = (peak * FFT_SCALE).coerceAtMost(1f)
            }
        }

        val kiaiMul = if (kiaiActive) 1f else 0.7f
        for (i in 0 until BAR_COUNT) {
            val target = temporalAmplitudes[(i + indexOffset) % BAR_COUNT] * kiaiMul
            if (target > frequencyAmplitudes[i]) frequencyAmplitudes[i] = target
        }

        indexOffset = (indexOffset + INDEX_CHANGE) % BAR_COUNT
    }

    companion object {
        const val BAR_COUNT = 120
        const val VISUALISER_ROUNDS = 3

        /** Room for the bars around the logo on each side, in virtual units. */
        const val MAX_EXTRA = 300f

        private const val VISUALISER_ROUNDS_LOW = 1
        private const val BAR_START_INSET = 0.88f
        private const val BAR_LENGTH = 2.5f
        private const val BAR_ALPHA = 0.15f
        private const val AMPLITUDE_DEAD_ZONE = 1f / 600f
        private const val DECAY_PER_MILLISECOND = 0.0024f
        private const val TIME_BETWEEN_UPDATES_FULL = 0.05f   // 20 Hz
        private const val TIME_BETWEEN_UPDATES_LOW = 0.10f   // 10 Hz
        private const val FFT_SCALE = 3f
        private const val INDEX_CHANGE = 5
        private const val BEAT_INDEX_JUMP = 10
    }
}
