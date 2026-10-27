package com.osudroid.ui.verktex.songmenu

import com.osudroid.ui.verktex.IPressable
import com.osudroid.ui.verktex.ISkinnable
import com.osudroid.ui.verktex.LegacyUnits
import com.osudroid.ui.verktex.LegacyUnits.vu
import com.osudroid.ui.verktex.SkinTextures
import com.reco1l.verktex.data.px
import com.reco1l.verktex.graphics.GraphicsTexture
import com.reco1l.verktex.input.InputEvent
import com.reco1l.verktex.input.PointerEvent
import com.reco1l.verktex.time.TickingClock
import com.reco1l.verktex.ui.UIAnimatedSprite
import ru.nsu.ccfit.zuev.osu.ResourceManager
import ru.nsu.ccfit.zuev.skins.OsuSkin

/**
 * A skin texture button of the song menu: back (animated), mods, options and random. The position
 * and size are virtual units, as the legacy sprites had them, applied in window pixels on update.
 *
 * Hit testing is left to the layer, which also keeps giving this button the events of the pointer
 * it consumed, as the AndEngine touch binding did. Must be used on the update thread.
 *
 * @param textureName The texture, or the base name of the frames (`<name>-<i>`) when [animated].
 * @param overTextureName The texture shown while pressed, null to keep [textureName].
 */
class SkinButton(
    private val textureName: String,
    private val overTextureName: String?,
    private val animated: Boolean
) : UIAnimatedSprite(), ISkinnable, IPressable {

    /** Whether the button grows to 1.25 while held. */
    var scaleWhenHold = false

    /** Called when a press starts. */
    var onPress: (() -> Unit)? = null

    /** Called when the press ends, with whether it was cancelled by moving away. */
    var onRelease: ((moved: Boolean) -> Unit)? = null

    /** Called when the press is cancelled by moving more than 50 virtual units. */
    var onCancel: (() -> Unit)? = null

    /** Called when the press is dropped by a cancel event or [cancelPress]. */
    var onPressDropped: (() -> Unit)? = null

    // Legacy geometry, in virtual units.
    var legacyX = 0f
        private set
    var legacyY = 0f
        private set
    var legacyWidth = 0f
        set(value) {
            field = value
            appliedScale = 0f
        }
    var legacyHeight = 0f
        set(value) {
            field = value
            appliedScale = 0f
        }

    /** Legacy `getWidthScaled()`. */
    val widthScaled: Float
        get() = legacyWidth * scaleX

    /** Legacy `getHeightScaled()`. */
    val heightScaled: Float
        get() = legacyHeight * scaleY

    private val press = LegacyPressTracker(this)

    private var texturesInvalid = false

    // LegacyUnits.scale the geometry was last applied for.
    private var appliedScale = 0f

    init {
        loadTextures()

        // The legacy sprites took the size of their first texture.
        legacyWidth = textureRegion?.width ?: 0f
        legacyHeight = textureRegion?.height ?: 0f
    }

    /** Legacy `setPosition`, in virtual units. */
    fun setPosition(x: Float, y: Float) {
        legacyX = x
        legacyY = y
        appliedScale = 0f
    }

    override fun onSkinChanged() {
        texturesInvalid = true
    }

    private fun loadTextures() {
        if (!animated) {
            textureRegion = SkinTextures.get(if (press.isPressed && overTextureName != null) overTextureName else textureName)
            return
        }

        // Legacy UIAnimatedSprite(textureName, withHyphen = true, framerate).
        val list = mutableListOf<GraphicsTexture.Region>()
        val frameCount = ResourceManager.getInstance().getFrameCount(textureName)

        for (i in 0 until frameCount) {
            SkinTextures.get("$textureName-$i")?.let(list::add)
        }

        if (list.isEmpty()) {
            SkinTextures.get(textureName)?.let(list::add)
        }

        frames = list

        val framerate = OsuSkin.get().animationFramerate
        val fps = if (framerate > 0) framerate else list.size.toFloat()

        if (fps > 0) {
            frameTime = 1f / fps
        }
    }

    override fun onUpdate(clock: TickingClock) {
        if (texturesInvalid) {
            texturesInvalid = false
            loadTextures()
        }

        if (appliedScale != LegacyUnits.scale) {
            appliedScale = LegacyUnits.scale

            x = legacyX.vu.px
            y = legacyY.vu.px
            width = legacyWidth.vu.px
            height = legacyHeight.vu.px
        }

        super.onUpdate(clock)
    }

    override fun onInputEvent(event: InputEvent): Boolean {
        if (event !is PointerEvent || !isVisible) {
            return false
        }

        // Local coordinates in virtual units, the slop is in virtual units.
        val local = toLocalSpaceFromScene(event.x, event.y)
        val localX = local.x / LegacyUnits.scale
        val localY = local.y / LegacyUnits.scale

        return when (event.action) {
            PointerEvent.Action.Down -> {
                if (press.down(localX, localY)) {
                    overTextureName?.let { textureRegion = SkinTextures.get(it) }
                    if (scaleWhenHold) scale = 1.25f
                    onPress?.invoke()
                }
                true
            }

            PointerEvent.Action.Up -> {
                if (press.up()) {
                    overTextureName?.let { textureRegion = SkinTextures.get(textureName) }
                    onRelease?.invoke(press.moved)
                }
                true
            }

            PointerEvent.Action.Move -> {
                if (press.move(localX, localY)) {
                    onCancel?.invoke()
                    overTextureName?.let { textureRegion = SkinTextures.get(textureName) }
                }
                false
            }

            // Cancel events are dispatched to every component, so they are not consumed.
            PointerEvent.Action.Unknown -> {
                cancelPress()
                false
            }
        }
    }

    override fun cancelPress() {
        if (!press.cancel()) {
            return
        }

        overTextureName?.let { textureRegion = SkinTextures.get(textureName) }
        if (scaleWhenHold) scale = 1f
        onPressDropped?.invoke()
    }
}
