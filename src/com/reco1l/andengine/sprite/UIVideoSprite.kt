package com.reco1l.andengine.sprite

import android.os.Handler
import android.os.Looper
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.util.UnstableApi
import androidx.annotation.OptIn
import com.reco1l.andengine.texture.*
import com.reco1l.verktex.Verktex
import com.reco1l.verktex.data.Anchor
import com.reco1l.verktex.data.px
import com.reco1l.verktex.ui.UISprite

@OptIn(UnstableApi::class)
class UIVideoSprite internal constructor(private val texture: VideoTexture) : UISprite() {

    constructor(source: String) : this(VideoTexture(source))

    /**
     * The width the sprite was sized to once the video reported its dimensions, 0 until then.
     */
    var videoWidth = 0f
        private set

    /**
     * The height the sprite was sized to once the video reported its dimensions, 0 until then.
     */
    var videoHeight = 0f
        private set

    init {
        val w = texture.videoWidth.toFloat()
        val h = texture.videoHeight.toFloat()
        textureRegion = texture.texture.region

        // The region covers the whole external texture, whose size is only nominal (the decoder sizes the buffers),
        // so the frame is stretched over the sprite, which is sized to the video.
        scaleType = ScaleType.Stretch

        // Some hardware decoders report 0×0 from prepare() and only provide dimensions
        // on the first decoded frame. Register a listener to size the sprite then.
        if (w <= 0f || h <= 0f) {
            texture.onReady = Runnable {
                Verktex.scheduleOnUpdate {
                    setVideoSize(texture.videoWidth.toFloat(), texture.videoHeight.toFloat())
                }
            }
        }
    }

    // -----------------------------------------------------------------------
    // Playback controls
    // -----------------------------------------------------------------------

    fun setOnReady(callback: Runnable) {
        texture.onReady = Runnable {
            Verktex.scheduleOnUpdate {
                // Update the Sprite dimensions now that the actual video resolution is known.
                // It was constructed as 0×0 because ExoPlayer reports dimensions asynchronously via onVideoSizeChanged.
                val w = texture.videoWidth.toFloat()
                val h = texture.videoHeight.toFloat()
                setVideoSize(w, h)

                // applyBackground's centering formula assumes that the scale is applied around the
                // center of the entity. The scale origin of a UIComponent is relative to its size
                // rather than in pixels, so passing half of the video size here would move the
                // video far off the screen.
                scaleOrigin = Anchor.Center

                mainHandler.post(callback)
            }
        }
    }

    private fun setVideoSize(w: Float, h: Float) {
        videoWidth = w
        videoHeight = h
        width = w.px
        height = h.px
    }

    fun release() {
        // Detaches the sprite under the tree lock, then posts a single ordered block to the main handler:
        // setVideoSurface(null) --> player.release() --> texture disposal on the draw thread.
        texture.release { removeSelf() }
    }

    fun play() {
        mainHandler.post { texture.player.play() }
    }

    fun pause() {
        mainHandler.post { texture.player.pause() }
    }

    fun seekTo(ms: Int) {
        mainHandler.post { texture.player.seekTo(ms.toLong()) }
    }

    fun setPlaybackSpeed(speed: Float) {
        mainHandler.post { texture.player.playbackParameters = PlaybackParameters(speed) }
    }

    protected fun finalize() {
        // Guard against a partially-constructed object: if VideoTexture's init threw,
        // texture.player is null at the bytecode level despite Kotlin's non-null type.
        try {
            release()
        } catch (_: Throwable) {}
    }

    companion object {
        private val mainHandler = Handler(Looper.getMainLooper())
    }
}
