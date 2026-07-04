package com.osudroid.storyboard.renderer

import android.graphics.Color
import com.edlplan.andengine.TextureHelper
import com.osudroid.storyboard.model.StoryboardAnimation
import com.osudroid.storyboard.model.StoryboardLayerType
import com.osudroid.storyboard.parser.StoryboardParser
import com.osudroid.storyboard.playback.StoryboardPlayback
import com.osudroid.beatmaps.hitobjects.BankHitSampleInfo
import com.osudroid.game.GameplayHitSampleInfo
import com.reco1l.andengine.component.UIComponent
import java.io.File
import kotlin.math.min
import kotlinx.coroutines.CoroutineScope
import org.andengine.engine.camera.Camera
import org.andengine.opengl.texture.region.TextureRegion
import org.andengine.opengl.util.GLState
import ru.nsu.ccfit.zuev.osu.Config

/**
 * Renders a beatmap's storyboard.
 *
 * This component renders the storyboard background and the `Background`, `Fail`/`Pass` and
 * `Foreground` layers, followed by the background dim, and is meant to be attached below all
 * gameplay elements. The `Overlay` layer is rendered by [overlayComponent], which is meant to be
 * attached above gameplay elements.
 *
 * All sprites are streamed through a shared [StoryboardBatch], with the storyboard's 640x480
 * coordinate space mapped to a centered letterbox that fills the screen height (or width,
 * whichever is limiting). Non-widescreen storyboards are cropped to the 4:3 area.
 */
class StoryboardComponent : UIComponent() {
    /**
     * The component rendering the `Overlay` layer, meant to be attached above gameplay elements.
     */
    @JvmField
    val overlayComponent = StoryboardOverlayComponent(this)

    /**
     * When `true`, the storyboard background is not drawn. Used when a background video is
     * playing so that the video shows through.
     */
    @JvmField
    var transparentBackground = false

    /**
     * The background brightness in the range `[0, 1]`. A dim overlay with `1 - brightness` alpha
     * is drawn above the storyboard layers (except the `Overlay` layer).
     */
    @JvmField
    var brightness = 1f

    /**
     * The playback of the loaded storyboard, or `null` if no storyboard is loaded.
     */
    var playback: StoryboardPlayback? = null
        private set

    private var texturePool: StoryboardTexturePool? = null
    private var loadedOsuPath: String? = null

    internal val batch = StoryboardBatch()

    /**
     * Whether a storyboard is loaded.
     */
    val isStoryboardAvailable
        get() = playback != null

    init {
        width = Config.getRES_WIDTH().toFloat()
        height = Config.getRES_HEIGHT().toFloat()
    }

    /**
     * Loads the storyboard of a beatmap. Parsing, playback compilation and texture loading are
     * performed on the calling thread, so this is meant to be called from a background thread.
     *
     * @param osuPath The path of the beatmap's `.osu` file.
     * @param scope The [CoroutineScope] to use for cancellation checkpoints.
     */
    @JvmOverloads
    fun load(osuPath: String, scope: CoroutineScope? = null) {
        if (osuPath == loadedOsuPath && playback != null) {
            return
        }

        release()

        val storyboard = StoryboardParser(osuPath, scope).parse() ?: return
        val pool = StoryboardTexturePool(File(osuPath).parentFile!!)

        pool.load(storyboard)

        texturePool = pool
        playback = StoryboardPlayback(storyboard)
        loadedOsuPath = osuPath
    }

    /**
     * Advances the storyboard playback to the given time. Seeking in both directions is
     * supported.
     *
     * This only updates the active sprite windows; the (expensive) per-sprite state evaluation
     * happens while drawing, so heavy storyboards slow down rendering at worst but can never
     * stall the update thread and with it the gameplay clock.
     *
     * @param timeMs The gameplay time in milliseconds.
     */
    fun updateTime(timeMs: Double) {
        playback?.setTime(timeMs)
    }

    /**
     * Updates the passing state, controlling the `Pass`/`Fail` layer visibility and
     * `Passing`/`Failing` triggers.
     *
     * @param passing Whether the player is in a passing state.
     */
    fun setPassingState(passing: Boolean) {
        val playback = playback ?: return

        playback.setPassing(passing, playback.currentTime)
    }

    /**
     * Notifies the storyboard that hit samples were played, activating matching hit sound
     * triggers.
     *
     * @param samples The played samples.
     */
    fun onHitSamplesPlayed(samples: List<GameplayHitSampleInfo>) {
        val playback = playback ?: return

        if (!playback.hasHitSoundTriggers) {
            return
        }

        for (i in samples.indices) {
            val sample = samples[i].sampleInfo as? BankHitSampleInfo ?: continue

            playback.onHitSound(sample.name, sample.bank, sample.customSampleBank, playback.currentTime)
        }
    }

    /**
     * Releases the loaded storyboard and unloads its textures.
     */
    fun release() {
        playback = null
        loadedOsuPath = null
        texturePool?.clear()
        texturePool = null
    }

    override fun doDraw(pGLState: GLState, pCamera: Camera) {
        beginDraw(pGLState)

        val playback = playback ?: return

        batch.begin(pGLState)
        drawBackground(playback)
        drawLayers(playback, MAIN_LAYERS, drawAlpha)
        drawDim()
        batch.end()
    }

    /**
     * Draws the given storyboard layers, letterboxed to the centered 640x480 storyboard space.
     * Shared with [StoryboardOverlayComponent]. The batch must have been started with
     * [StoryboardBatch.begin] by the caller.
     */
    internal fun drawLayers(
        playback: StoryboardPlayback,
        layers: Array<StoryboardLayerType>,
        alpha: Float
    ) {
        val pool = texturePool ?: return

        val screenWidth = width
        val screenHeight = height
        val scale = min(screenWidth / STORYBOARD_WIDTH, screenHeight / STORYBOARD_HEIGHT)
        val offsetX = (screenWidth - STORYBOARD_WIDTH * scale) / 2
        val offsetY = (screenHeight - STORYBOARD_HEIGHT * scale) / 2

        // Note: unlike lazer, the storyboard is intentionally NOT masked to its 4:3/16:9 box.
        // osu!stable and the previous osu-droid renderer never cropped storyboards, and sprites
        // outside the box are expected to be visible on wide screens.
        batch.setSpace(offsetX, offsetY, scale)

        for (layer in layers) {
            if (!playback.isLayerVisible(layer)) {
                continue
            }

            val sprites = playback.activeSprites(layer)

            for (i in sprites.indices) {
                val sprite = sprites[i]

                sprite.update(playback.currentTime)

                if (sprite.alpha * alpha < ALPHA_EPSILON) {
                    continue
                }

                val element = sprite.element
                val path = (element as? StoryboardAnimation)?.framePath(sprite.frameIndex) ?: element.filePath

                batch.draw(sprite, pool.get(path), alpha)
            }
        }

        batch.resetSpace()
    }

    private fun drawBackground(playback: StoryboardPlayback) {
        if (transparentBackground) {
            return
        }

        val storyboard = playback.storyboard
        val backgroundFilename = storyboard.backgroundFilename

        if (storyboard.usesBackgroundImage() || backgroundFilename == null) {
            // The storyboard displays the background itself - cover the static background
            // with black.
            batch.drawQuad(blackRegion, 0f, 0f, width, height, 0f, 0f, 0f, 1f)
            return
        }

        val region = texturePool?.get(backgroundFilename) ?: return
        val scale = min(width / region.width, height / region.height)
        val backgroundWidth = region.width * scale
        val backgroundHeight = region.height * scale
        val left = (width - backgroundWidth) / 2
        val top = (height - backgroundHeight) / 2

        batch.drawQuad(
            blackRegion, 0f, 0f, width, height,
            0f, 0f, 0f, 1f
        )
        batch.drawQuad(
            region,
            left, top, left + backgroundWidth, top + backgroundHeight,
            1f, 1f, 1f, drawAlpha
        )
    }

    private fun drawDim() {
        val dimAlpha = (1f - brightness) * drawAlpha

        if (dimAlpha < ALPHA_EPSILON) {
            return
        }

        batch.drawQuad(blackRegion, 0f, 0f, width, height, 0f, 0f, 0f, dimAlpha)
    }

    companion object {
        const val STORYBOARD_WIDTH = 640f
        const val STORYBOARD_HEIGHT = 480f

        private const val ALPHA_EPSILON = 1f / 255f

        private val MAIN_LAYERS = arrayOf(
            StoryboardLayerType.Background,
            StoryboardLayerType.Fail,
            StoryboardLayerType.Pass,
            StoryboardLayerType.Foreground
        )

        private val blackRegion: TextureRegion by lazy {
            TextureHelper.create1xRegion(Color.argb(255, 0, 0, 0))
        }
    }
}
