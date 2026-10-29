package com.osudroid.game

import android.net.Uri
import android.util.Log
import android.os.Looper
import android.content.Context
import android.os.Handler
import androidx.media3.common.*
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.*
import androidx.media3.exoplayer.mediacodec.MediaCodecSelector
import androidx.media3.exoplayer.video.VideoRendererEventListener
import androidx.media3.decoder.ffmpeg.ExperimentalFfmpegVideoRenderer
import com.osudroid.verktex.bridge.VerktexBridge
import com.reco1l.verktex.Verktex
import com.reco1l.verktex.android.gles.ESExternalTexture
import com.reco1l.verktex.graphics.GraphicsTexture
import ru.nsu.ccfit.zuev.osu.GlobalManager
import java.io.*
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

@UnstableApi
class VideoTexture(
    val source: String,
    playerFactory: (Context) -> ExoPlayer = ::createPlayer,
    textureFactory: (Float, Float) -> GraphicsTexture = { w, h -> Verktex.graphics.createExternalTexture(w, h) }
) {

    val player: ExoPlayer

    private val onReadyRef = AtomicReference<Runnable?>()

    /**
     * The callback that is invoked when the video dimensions are available. This is necessary because the dimensions
     * are not known until the video is prepared, which happens asynchronously.
     *
     * The callback will be invoked on the main thread exactly once.
     */
    var onReady
        get() = onReadyRef.get()
        set(value) {
            onReadyRef.set(value)

            // Handle the race where dimensions arrived before the callback was registered.
            // Uses getAndSet(null) so only one caller (this path or onVideoSizeChanged) wins.
            if (videoWidth > 0) {
                mainHandler.post { onReadyRef.getAndSet(null)?.run() }
            }
        }

    @Volatile
    var videoWidth = 0
        private set

    @Volatile
    var videoHeight = 0
        private set

    /**
     * The external texture the player decodes into. It owns its [android.view.Surface] and
     * [android.graphics.SurfaceTexture], which it creates and releases on the draw thread; its region always covers
     * it whole (UV 0..1) and the decoder sizes the buffers, so its own size is only nominal.
     */
    val texture: ESExternalTexture

    private val released = AtomicBoolean(false)

    init {
        val context = GlobalManager.getInstance().mainActivity.applicationContext

        // ExoPlayer's builder is thread-safe, but subsequent API calls are not.
        // ExoPlayer enforces that they run on its designated looper (main thread here).
        player = playerFactory(context)

        mainHandler.post {
            player.volume = 0f
            player.repeatMode = Player.REPEAT_MODE_OFF
            player.addListener(object : Player.Listener {
                override fun onVideoSizeChanged(videoSize: VideoSize) {
                    if (videoSize.width == 0 || videoSize.height == 0) {
                        return
                    }

                    videoWidth = videoSize.width
                    videoHeight = videoSize.height
                    onReadyRef.getAndSet(null)?.run()
                }

                override fun onPlayerError(error: PlaybackException) {
                    Log.e("VideoTexture", "ExoPlayer error occurred", error)
                }
            })
            player.setMediaItem(MediaItem.fromUri(Uri.fromFile(File(source))))
            player.prepare()
        }

        texture = textureFactory(NOMINAL_SIZE, NOMINAL_SIZE) as ESExternalTexture

        // The surface is (re)created on the draw thread, on the first load and after a context loss, but
        // setVideoSurface must be called on ExoPlayer's main-thread looper.
        texture.onSurfaceRecreated = {
            val surface = texture.surface
            mainHandler.post {
                if (!released.get()) {
                    player.setVideoSurface(surface)
                }
            }
        }

        // Loaded right away, not on the first draw: a decoder (the FFmpeg one in particular) may report no size
        // until it has a surface to decode into, and the sprite is only drawn once the size is known.
        Verktex.scheduleOnDraw { texture.load() }
    }

    /**
     * Releases the video. [onDetached] runs first, on the update thread under the tree lock, to detach what draws
     * the texture; then, on the main thread, the player's surface is cleared so the codec thread stops writing to it
     * and the player is released; the texture (with its surface) is disposed last, on the draw thread.
     *
     * Only the first call has an effect.
     */
    fun release(onDetached: Runnable) {
        if (!released.compareAndSet(false, true)) {
            return
        }

        texture.onSurfaceRecreated = null

        if (VerktexBridge.isUpdateThread) {
            Verktex.withTreeLock { onDetached.run() }
            releasePlayer()
        } else {
            Verktex.scheduleOnUpdate {
                onDetached.run()
                releasePlayer()
            }
        }
    }

    private fun releasePlayer() {
        mainHandler.post {
            // Order matters:
            // 1. Clear ExoPlayer's surface so the codec thread stops writing to the native window.
            // 2. Release the player.
            // 3. Release the texture and its surface on the draw thread, after the player is fully torn down.
            try {
                player.setVideoSurface(null)
            } catch (_: Exception) {}

            player.release()

            Verktex.scheduleOnDraw {
                texture.dispose()
                Verktex.graphics.unregisterResource(texture)
            }
        }
    }

    companion object {
        private val mainHandler = Handler(Looper.getMainLooper())

        /**
         * The size the external texture is created with, before the video's size is known.
         */
        private const val NOMINAL_SIZE = 16f

        private fun createPlayer(context: Context): ExoPlayer {
            // DefaultRenderersFactory.EXTENSION_RENDERER_MODE_ON auto-discovers FfmpegAudioRenderer via reflection.
            // ExperimentalFfmpegVideoRenderer must be appended manually because DefaultRenderersFactory looks for the old
            // name "FfmpegVideoRenderer" (since renamed).
            val renderersFactory = object : DefaultRenderersFactory(context) {
                override fun buildVideoRenderers(
                    context: Context,
                    extensionRendererMode: Int,
                    mediaCodecSelector: MediaCodecSelector,
                    enableDecoderFallback: Boolean,
                    eventHandler: Handler,
                    eventListener: VideoRendererEventListener,
                    allowedVideoJoiningTimeMs: Long,
                    out: ArrayList<Renderer>
                ) {
                    super.buildVideoRenderers(context, extensionRendererMode, mediaCodecSelector,
                        enableDecoderFallback, eventHandler, eventListener, allowedVideoJoiningTimeMs, out)

                    out.add(ExperimentalFfmpegVideoRenderer(
                        allowedVideoJoiningTimeMs, eventHandler, eventListener, 50))
                }
            }.setExtensionRendererMode(DefaultRenderersFactory.EXTENSION_RENDERER_MODE_ON)

            return ExoPlayer.Builder(context, renderersFactory)
                .setLooper(Looper.getMainLooper())
                .build()
        }
    }
}
