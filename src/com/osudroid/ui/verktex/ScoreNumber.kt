package com.osudroid.ui.verktex

import com.reco1l.verktex.Logger
import com.reco1l.verktex.data.px
import com.reco1l.verktex.graphics.GraphicsTexture
import com.reco1l.verktex.math.Easing
import com.reco1l.verktex.math.FloatAnimation
import com.reco1l.verktex.time.TickingClock
import com.reco1l.verktex.ui.UISprite
import com.reco1l.verktex.ui.container.UIContainer
import ru.nsu.ccfit.zuev.skins.OsuSkin

/**
 * A number drawn with the skin's score digits, the Verktex twin of the legacy
 * [ru.nsu.ccfit.zuev.osu.scoring.ScoreNumber]. Values are in the legacy virtual units.
 *
 * The legacy layout is kept as is: letters are sized by [textScale] but placed by their sized width
 * times [textScale] again, so they only sit next to each other at a scale of 1.
 */
class ScoreNumber @JvmOverloads constructor(
    x: Float,
    y: Float,
    text: String,
    private val textScale: Float,
    private val center: Boolean,
    private val lookup: (name: String) -> GraphicsTexture.Region? = ::skinLookup
) : UIContainer(), ISkinnable {

    private var text = ""

    private val bounces = mutableListOf<Bounce>()

    /**
     * The height of the letters, 0 until a non-empty text was set.
     */
    var textHeight = 0f
        private set


    init {
        this.x = x.px
        this.y = y.px
        setText(text)
    }


    /**
     * Replaces the letters with the ones for [text].
     */
    fun setText(text: String) {
        this.text = text

        clearChildren()
        bounces.clear()

        var totalWidth = 0f
        val letters = mutableListOf<UISprite>()
        val positions = mutableListOf<Float>()

        for (ch in text) {
            val name = textureNameFor(ch)
            val region = lookup(name)

            if (region == null) {
                Logger.w(TAG, "Missing score texture \"$name\", skipping '$ch'.")
                continue
            }

            val width = region.width * textScale
            val height = region.height * textScale

            positions += totalWidth * textScale
            letters += UISprite(region).apply {
                scaleType = UISprite.ScaleType.Stretch
                this.x = positions.last().px
                this.width = width.px
                this.height = height.px
            }

            totalWidth += width * textScale
            textHeight = height * textScale
        }

        if (center) {
            totalWidth /= 2 * textScale

            letters.forEachIndexed { i, letter ->
                letter.x = (positions[i] - totalWidth).px
                letter.scale = textScale
                bounces += Bounce(letter)
            }
        }

        letters.forEach { this += it }
    }


    override fun onUpdate(clock: TickingClock) {
        if (bounces.isNotEmpty()) {
            bounces.forEach { it.update(clock) }
            bounces.removeAll { it.isFinished }
        }

        super.onUpdate(clock)
    }

    override fun onSkinChanged() {
        setText(text)
    }


    /**
     * The legacy `ScaleModifier` sequence: up to 1.5 times the scale in 0.2 s, back in 0.4 s.
     */
    private inner class Bounce(private val letter: UISprite) {

        private val animation = FloatAnimation(textScale, 0f, Easing.None).apply {
            animateTo(textScale * 1.5f, GROW_DURATION, Easing.None)
        }

        private var elapsed = 0f

        private var shrinking = false

        var isFinished = false
            private set

        fun update(clock: TickingClock) {
            elapsed += clock.elapsedTickTime

            if (!shrinking && elapsed >= GROW_DURATION) {
                animation.jumpTo(textScale * 1.5f)
                animation.animateTo(textScale, SHRINK_DURATION, Easing.None)
                shrinking = true
            }

            letter.scale = animation.update(clock)

            if (shrinking && elapsed >= GROW_DURATION + SHRINK_DURATION) {
                letter.scale = textScale
                isFinished = true
            }
        }
    }


    companion object {

        private const val TAG = "ScoreNumber"

        private const val GROW_DURATION = 0.2f

        private const val SHRINK_DURATION = 0.4f


        /**
         * The texture name for [ch] without the skin's score prefix.
         */
        @JvmStatic
        fun textureNameFor(ch: Char): String = when (ch) {
            in '0'..'9' -> ch.toString()
            '.', ',' -> "comma"
            '%' -> "percent"
            else -> "x"
        }

        /**
         * Looks up "<prefix>-<name>" with the skin's score prefix, falling back to the default
         * prefix like `ResourceManager.getTextureWithPrefix`. Must be called on the update thread.
         */
        @JvmStatic
        fun skinLookup(name: String): GraphicsTexture.Region? {
            val prefix = OsuSkin.get().scorePrefix

            return SkinTextures.get("${prefix.currentValue}-$name")
                ?: if (prefix.isDefault) null else SkinTextures.get("${prefix.defaultValue}-$name")
        }
    }
}
