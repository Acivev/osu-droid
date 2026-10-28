package com.osudroid.game.replay

import com.osudroid.ui.verktex.LegacyUnits.vu
import com.osudroid.ui.verktex.SkinTextures
import com.reco1l.verktex.data.Anchor
import com.reco1l.verktex.data.Padding
import com.reco1l.verktex.data.fill
import com.reco1l.verktex.data.px
import com.reco1l.verktex.ui.UISprite
import com.reco1l.verktex.ui.button
import com.reco1l.verktex.ui.container.UILinearContainer
import com.reco1l.verktex.ui.container.linearContainer
import com.reco1l.verktex.ui.form.FormSlider
import com.reco1l.verktex.ui.sprite
import com.reco1l.verktex.ui.textButton
import java.text.DecimalFormat
import kotlin.math.abs

class ReplayPlaybackSeek : UILinearContainer() {
    /**
     * Called when the user seeks to a position. The argument is the target time in seconds.
     */
    var onSeek: ((Float) -> Unit)? = null

    /**
     * Whether the user has paused playback via the play/pause button.
     */
    var isPlaybackPaused = false
        private set

    /**
     * Called when the user presses the play/pause button. The argument is `true` when pausing, `false` when resuming.
     */
    var onPauseToggle: ((Boolean) -> Unit)? = null

    private var minDuration = 0f
    private var totalDuration = 0f
    private var rawCurrentSeconds = 0f
    private var isUserDragging = false

    private val secondsFormatter = DecimalFormat("00")

    private val seekBar = FormSlider().apply {
        label = "Seek"
        control.min = 0f
        control.max = 0f
        showResetButton = false

        valueFormatter = {
            val seconds = (rawCurrentSeconds - minDuration).toInt()
            val duration = (totalDuration - minDuration).toInt().coerceAtLeast(0)

            val sAbs = abs(seconds)
            val sign = if (seconds < 0) "-" else ""

            "$sign${sAbs / 60}:${secondsFormatter.format(sAbs % 60)} / ${duration / 60}:${secondsFormatter.format(duration % 60)}"
        }

        onValueChanged = {
            if (isUserDragging) {
                rawCurrentSeconds = it
            }
        }

        control.onStartDragging = { isUserDragging = true }

        control.onStopDragging = {
            isUserDragging = false
            onSeek?.invoke(value)
        }
    }

    init {
        orientation = Orientation.Vertical
        width = fill()

        +seekBar

        linearContainer {
            spacing = 10f.vu.px
            padding = Padding(0f.px, 16f.vu.px)
            anchor = Anchor.TopCenter
            origin = Anchor.TopCenter

            fun addSeekButton(delta: Float) = textButton {
                text = "%+d s".format(delta.toInt())
                height = 42f.vu.px
                padding = Padding(12f.vu.px, 0f.px)
                onActionUp = { onSeek?.invoke((seekBar.value + delta).coerceIn(minDuration, totalDuration)) }
            }

            addSeekButton(-10f)
            addSeekButton(-1f)

            // The AndEngine icon button took a texture, the Verktex one takes a font icon: a button with the sprite the
            // AndEngine one had.
            button {
                height = 42f.vu.px
                padding = Padding(12f.vu.px, 0f.px)

                val icon = sprite {
                    scaleType = UISprite.ScaleType.Fit
                    anchor = Anchor.Center
                    origin = Anchor.Center
                    width = 28f.vu.px
                    height = 28f.vu.px
                    textureRegion = SkinTextures.getAsset("music_pause", "music_pause.png")
                }

                onActionUp = {
                    isPlaybackPaused = !isPlaybackPaused
                    icon.textureRegion = if (isPlaybackPaused) {
                        SkinTextures.getAsset("music_play", "music_play.png")
                    } else {
                        SkinTextures.getAsset("music_pause", "music_pause.png")
                    }
                    onPauseToggle?.invoke(isPlaybackPaused)
                }
            }

            addSeekButton(1f)
            addSeekButton(10f)
        }
    }

    /**
     * Updates the seek bar to reflect the current playback position.
     * Should be called from the game update thread each frame.
     *
     * @param currentSeconds The current playback position in seconds.
     * @param minSeconds The earliest seekable position in seconds (first object's start time).
     * @param maxSeconds The latest seekable position in seconds (last object's end time).
     */
    fun updateSeekPosition(currentSeconds: Float, minSeconds: Float, maxSeconds: Float) {
        if (isUserDragging) {
            return
        }

        minDuration = minSeconds
        totalDuration = maxSeconds
        rawCurrentSeconds = currentSeconds
        // The Verktex slider clamps its value into min..max on each change, so the range must not be empty in between.
        if (minSeconds > seekBar.control.max) {
            seekBar.control.max = maxSeconds
            seekBar.control.min = minSeconds
        } else {
            seekBar.control.min = minSeconds
            seekBar.control.max = maxSeconds
        }

        if (currentSeconds < minSeconds) {
            // During lead-in, the slider stays pinned at min, so its value won't change and
            // valueFormatter won't be triggered automatically. Force a refresh to update the displayed time.
            seekBar.onControlValueChanged()
        } else {
            seekBar.value = currentSeconds.coerceAtMost(maxSeconds)
        }
    }
}