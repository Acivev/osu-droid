package com.reco1l.verktex.ui

import com.reco1l.verktex.graphics.GraphicsTexture
import com.reco1l.verktex.time.TickingClock
import com.reco1l.verktex.ui.container.UIContainer


inline fun UIContainer.animatedSprite(builder: UIAnimatedSprite.() -> Unit): UIAnimatedSprite {
    return UIAnimatedSprite().apply(builder).also(::plusAssign)
}

/**
 * Sprite that plays a sequence of texture regions as an animation.
 */
open class UIAnimatedSprite(frames: List<GraphicsTexture.Region> = emptyList()) : UISprite(frames.firstOrNull()) {

    /**
     * List of frames for the animation.
     *
     * Changing this value will restart the animation from the first frame.
     */
    var frames = frames
        set(value) {
            if (field != value) {
                field = value
                elapsedSec = 0f
                frameIndex = 0
                textureRegion = value.firstOrNull()
            }
        }

    /**
     * The duration of each frame in seconds.
     */
    var frameTime = DEFAULT_FRAME_TIME
        set(value) {
            require(value > 0f) { "The frame time must be greater than 0." }
            field = value
        }

    /**
     * Whether the animation should loop.
     */
    var isLoop = true

    /**
     * The elapsed time since the animation started.
     */
    var elapsedSec = 0f

    /**
     * Whether the animation is playing.
     */
    var isPlaying = true
        private set

    /**
     * The current frame index.
     */
    var frameIndex = 0
        private set(value) {
            if (field != value) {
                field = value
                textureRegion = frames.getOrNull(value)
            }
        }


    /**
     * The duration of the animation in seconds.
     */
    val duration
        get() = frames.size * frameTime


    override fun onUpdate(clock: TickingClock) {

        if (frames.isNotEmpty()) {

            if (isPlaying) {
                elapsedSec += clock.elapsedTickTime

                if (elapsedSec >= duration) {

                    if (isLoop) {
                        elapsedSec %= duration
                    } else {
                        elapsedSec = duration
                        isPlaying = false
                    }
                }
            }

            frameIndex = (elapsedSec / frameTime).toInt().coerceIn(0, frames.size - 1)
        }

        super.onUpdate(clock)
    }


    fun play() {
        isPlaying = true
    }

    fun pause() {
        isPlaying = false
    }

    fun stop() {
        isPlaying = false
        elapsedSec = 0f
        frameIndex = 0
    }


    companion object {

        const val DEFAULT_FRAME_TIME = 1f / 60f

    }

}
