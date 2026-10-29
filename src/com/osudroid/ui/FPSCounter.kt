package com.osudroid.ui

import androidx.annotation.VisibleForTesting
import com.edlplan.framework.easing.Easing
import com.reco1l.framework.Color4
import com.osudroid.math.Interpolation
import com.osudroid.ui.verktex.LegacyUnits
import com.osudroid.ui.verktex.LegacyUnits.vu
import com.osudroid.ui.verktex.times
import com.osudroid.verktex.bridge.VerktexBridge
import com.reco1l.verktex.data.Anchor
import com.reco1l.verktex.data.Padding
import com.reco1l.verktex.data.px
import com.reco1l.verktex.time.TickingClock
import com.reco1l.verktex.ui.UIComponent
import com.reco1l.verktex.ui.text.UIText
import com.rian.andengine.modifier.fadeIn
import com.rian.andengine.modifier.fadeTo
import com.rian.andengine.timing.ThrottledFrameClock
import java.util.Formatter
import java.util.Locale
import kotlin.math.min
import kotlin.math.roundToInt
import ru.nsu.ccfit.zuev.osu.Config
import ru.nsu.ccfit.zuev.osu.GlobalManager.getInstance as getGlobal

/**
 * Represents an FPS counter.
 */
class FPSCounter : UIText() {
    // The update loop's clock, which the Verktex update is pumped from.
    private val updateClock
        get() = clockProvider()

    /**
     * The clock the frame rate is measured with. Replaced by the tests.
     */
    @VisibleForTesting
    internal var clockProvider: () -> ThrottledFrameClock = { VerktexBridge.updateLoop.clock }

    /**
     * The display refresh rate. Replaced by the tests.
     */
    @VisibleForTesting
    internal var refreshRateProvider: () -> Float = { getGlobal().mainActivity.refreshRate }

    //region Counting logic

    /**
     * The current frame rate.
     */
    var displayedFpsCount = 0f
        private set

    /**
     * The average frame time in seconds.
     */
    var displayedFrameTime = 0f
        private set

    private var aimFPS = 0f

    private val spikeTime = 0.02f
    private val dampTime = 0.1f

    private val stringBuilder = StringBuilder(24)
    private val formatter = Formatter(stringBuilder, Locale.US)

    //endregion

    //region Display logic

    private var lastDisplayedFps = 0
    private var lastDisplayedFrameTime = 0f

    private var timeSinceLastUpdate = 0f
    private val updateInterval = 0.1f

    private var isDisplayed = false
    private var timeSinceLastSignificantChange = 0f
    private var wasVisible = false

    private val idleAlpha = 0.7f
    private val displayTimeout = 2f
    private val fadeDuration = 0.3f

    private val minimumTextColor = Color4(0xed1121)
    private val middleTextColor = Color4(0xebc247)
    private val maximumTextColor = Color4(0xccff99)

    // LegacyUnits.scale the sizes were last applied for.
    private var appliedScale = 0f

    init {
        wrapText = false
        anchor = Anchor.BottomRight
        origin = Anchor.BottomRight
        alignment = Anchor.Center

        style = Style { theme ->
            backgroundColor = (theme.palette.accent * 0.1f).copy(alpha = 0.8f)
        }
    }

    override fun onUpdate(clock: TickingClock) {
        // The font size, padding and corner radius are in virtual units.
        if (appliedScale != LegacyUnits.scale) {
            appliedScale = LegacyUnits.scale

            fontSize = LegacyUnits.fontSize("smallFont").vu.px
            padding = Padding(6f.vu.px, 4f.vu.px)
            radius = 8f.vu.px
        }

        isVisible = Config.isShowFPS()

        if (isVisible) {
            if (!wasVisible) {
                requestDisplay()
            }

            wasVisible = true
            timeSinceLastSignificantChange += updateClock.timeInfo.elapsed

            updateFPS()

            if (isDisplayed && timeSinceLastSignificantChange > displayTimeout) {
                fadeTo(idleAlpha, fadeDuration, Easing.OutQuint)
                isDisplayed = false
            }
        } else {
            wasVisible = false
        }

        super.onUpdate(clock)
    }

    private fun requestDisplay() {
        timeSinceLastSignificantChange = 0f

        if (!isDisplayed) {
            fadeIn(fadeDuration, Easing.OutQuint)
            isDisplayed = true
        }
    }

    private fun updateFPS() {
        val elapsedUpdateFrameTime = updateClock.elapsedFrameTime

        // If the game goes into a suspended state (i.e., debugger attached), we want to ignore really long periods of
        // no processing.
        if (elapsedUpdateFrameTime > 10) {
            return
        }

        // Handle the case where the window has become inactive (we want to show the FPS as it is changing, even if it
        // is not an outlier).
        val aimRatesChanged = updateAimFPS()
        val hasUpdateSpike = displayedFrameTime < spikeTime && elapsedUpdateFrameTime > spikeTime

        displayedFrameTime = Interpolation.dampContinuously(
            displayedFrameTime,
            elapsedUpdateFrameTime,
            if (hasUpdateSpike) 0f else dampTime,
            elapsedUpdateFrameTime
        )

        displayedFpsCount = if (hasUpdateSpike) {
            // Show spike time using raw elapsed value, to account for `framesPerSecond` being so averaged spike frames
            // do not show.
            1f / elapsedUpdateFrameTime
        } else {
            Interpolation.dampContinuously(
                displayedFpsCount,
                updateClock.framesPerSecond,
                dampTime,
                updateClock.timeInfo.elapsed
            )
        }

        // Force update if we are below the target by a certain threshold.
        val hasSignificantChanges = aimRatesChanged || hasUpdateSpike || displayedFpsCount < aimFPS * 0.6f

        if (hasSignificantChanges) {
            requestDisplay()
        }

        timeSinceLastUpdate += updateClock.timeInfo.elapsed

        if (!hasSignificantChanges && timeSinceLastUpdate < updateInterval) {
            return
        }

        val displayedFps = displayedFpsCount.roundToInt()
        val isHighPrecision = displayedFrameTime < 0.01f
        val displayedMs = displayedFrameTime * 1000

        val roundedFrameTime = if (isHighPrecision) {
            (displayedMs * 10).roundToInt() / 10f
        } else {
            displayedMs.roundToInt().toFloat()
        }

        if (!hasSignificantChanges && displayedFps == lastDisplayedFps && roundedFrameTime == lastDisplayedFrameTime) {
            return
        }

        timeSinceLastUpdate = 0f
        lastDisplayedFps = displayedFps
        lastDisplayedFrameTime = roundedFrameTime

        stringBuilder.setLength(0)

        formatter.format(
            "%.${if (isHighPrecision) "1" else "0"}f ms | %d FPS",
            displayedMs,
            displayedFps
        )

        text = stringBuilder.toString()

        updateColor()
    }

    private fun updateAimFPS(): Boolean {
        val refreshRate = refreshRateProvider()

        val newAimFPS = if (updateClock.throttling && updateClock.maximumUpdateHz > 0) {
            min(updateClock.maximumUpdateHz, refreshRate)
        } else {
            refreshRate
        }

        if (aimFPS != newAimFPS) {
            aimFPS = newAimFPS
            return true
        }

        return false
    }

    private fun updateColor() {
        val performanceRatio = displayedFpsCount / aimFPS
        val red: Float
        val green: Float
        val blue: Float

        if (performanceRatio < 0.5f) {
            val t = (performanceRatio / 0.5f).coerceIn(0f, 1f)

            red = Interpolation.linear(minimumTextColor.red, middleTextColor.red, t)
            green = Interpolation.linear(minimumTextColor.green, middleTextColor.green, t)
            blue = Interpolation.linear(minimumTextColor.blue, middleTextColor.blue, t)
        } else {
            val t = ((performanceRatio - 0.5f) / 0.4f).coerceIn(0f, 1f)

            red = Interpolation.linear(middleTextColor.red, maximumTextColor.red, t)
            green = Interpolation.linear(middleTextColor.green, maximumTextColor.green, t)
            blue = Interpolation.linear(middleTextColor.blue, maximumTextColor.blue, t)
        }

        color = color.copy(red = red, green = green, blue = blue)
    }

    //endregion

    //region Reset

    // The AndEngine entity reset (transformations, colour, modifiers) has no Verktex counterpart.
    fun reset() {
        displayedFpsCount = 0f
        displayedFrameTime = 0f
        lastDisplayedFps = 0
        lastDisplayedFrameTime = 0f
        timeSinceLastUpdate = 0f
        isDisplayed = false
        timeSinceLastSignificantChange = 0f
        wasVisible = false
    }

    //endregion

    companion object {

        /**
         * The counter shown above every screen, set when it is added at startup.
         */
        @JvmStatic
        var instance: FPSCounter? = null

        /**
         * The overlay root holding [instance] over the whole window, the component kept above the
         * gameplay layer.
         */
        @JvmStatic
        var root: UIComponent? = null
    }
}
