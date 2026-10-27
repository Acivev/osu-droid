package com.osudroid.ui.v1

import com.osudroid.GameMode
import com.osudroid.beatmaps.DroidHitWindow
import com.osudroid.beatmaps.HitWindow
import com.osudroid.beatmaps.PreciseDroidHitWindow
import com.osudroid.beatmaps.hitobjects.HitObject
import com.osudroid.beatmaps.sections.BeatmapDifficulty
import com.osudroid.mods.Mod
import com.osudroid.mods.ModPrecise
import com.osudroid.ui.OsuColors
import com.osudroid.ui.verktex.LegacyUnits
import com.osudroid.ui.verktex.LegacyUnits.vu
import com.osudroid.ui.verktex.times
import com.osudroid.ui.verktex.toVerktex
import com.osudroid.utils.CircleSizeCalculator
import com.osudroid.utils.applyMods
import com.osudroid.utils.calculateRate
import com.osudroid.verktex.bridge.EngineRouter
import com.reco1l.framework.math.roundBy
import com.reco1l.verktex.Verktex
import com.reco1l.verktex.data.Anchor
import com.reco1l.verktex.data.Axis
import com.reco1l.verktex.data.ClipMode
import com.reco1l.verktex.data.Color4
import com.reco1l.verktex.data.LayoutConstraints
import com.reco1l.verktex.data.Padding
import com.reco1l.verktex.data.Vec2
import com.reco1l.verktex.data.Vec4
import com.reco1l.verktex.data.fill
import com.reco1l.verktex.data.px
import com.reco1l.verktex.input.InputEvent
import com.reco1l.verktex.input.KeyboardEvent
import com.reco1l.verktex.input.PointerEvent
import com.reco1l.verktex.math.Easing
import com.reco1l.verktex.math.FloatAnimation
import com.reco1l.verktex.time.TickingClock
import com.reco1l.verktex.ui.Theme
import com.reco1l.verktex.ui.UIModal
import com.reco1l.verktex.ui.container.UIContainer
import com.reco1l.verktex.ui.container.UILinearContainer
import com.reco1l.verktex.ui.container.UIScrollableContainer
import com.reco1l.verktex.ui.text.UIText
import kotlin.math.roundToInt

/**
 * A router overlay containing a [UIModal] that shows the attributes of a beatmap, adjusted for the given mods.
 *
 * @param difficulty The base [BeatmapDifficulty] of the beatmap.
 * @param mods The [Mod]s to adjust the [BeatmapDifficulty] with.
 */
open class BeatmapAttributeDisplay(difficulty: BeatmapDifficulty, mods: Iterable<Mod>) : UIContainer() {

    // The components' constructors read engine state (fonts, theme) the draw and update workers use.
    // The overlay is the router-tracked part, so the modal has a parent and does not add itself.
    private val modal = Verktex.withTreeLock { BeatmapAttributeModal() }
    private val modalCard = modal.card[0] as UILinearContainer

    // Texts and the ResourceManager font they used.
    private val texts = mutableListOf<Pair<UIText, String>>()

    // LegacyUnits.scale the sizes were last applied for.
    private var appliedScale = 0f


    init {
        Verktex.withTreeLock {
            +modal

            val nonRateAdjustedDifficulty = difficulty.clone()
            val rateAdjustedDifficulty = difficulty.clone()

            nonRateAdjustedDifficulty.applyMods(GameMode.Droid, mods, false)
            rateAdjustedDifficulty.applyMods(GameMode.Droid, mods, true)

            // CS

            addAttribute(
                "CS (Circle Size)",
                "Affects the size of hit circles and sliders.",
                difficulty.difficultyCS,
                rateAdjustedDifficulty.difficultyCS,
                arrayOf(
                    Metric(
                        "Hit circle radius",
                        (HitObject.OBJECT_RADIUS * CircleSizeCalculator.droidCSToDroidScale(rateAdjustedDifficulty.difficultyCS))
                            .roundBy(1)
                            .toString()
                    )
                )
            )

            // AR

            val approachTime = BeatmapDifficulty.difficultyRangeInt(
                rateAdjustedDifficulty.ar.toDouble(),
                HitObject.PREEMPT_MAX,
                HitObject.PREEMPT_MID,
                HitObject.PREEMPT_MIN
            )

            addAttribute(
                "AR (Approach Rate)",
                "Affects how early objects appear on screen relative to their hit time.",
                difficulty.ar,
                rateAdjustedDifficulty.ar,
                arrayOf(Metric("Approach time", "${approachTime}ms"))
            )

            // OD
            // For OD, we want to display rate-affected hit window values in a different way, because they are not
            // accurately represented by the OD value itself.
            val isPrecise = mods.any { it is ModPrecise }
            val rate = mods.calculateRate(Double.POSITIVE_INFINITY)

            val hitWindow =
                if (isPrecise) PreciseDroidHitWindow(nonRateAdjustedDifficulty.od)
                else DroidHitWindow(nonRateAdjustedDifficulty.od)

            addAttribute(
                "OD (Overall Difficulty)",
                "Affects timing requirements for hit circles and spin speed requirements for spinners.",
                difficulty.od,
                rateAdjustedDifficulty.od,
                arrayOf(
                    Metric("GREAT hit window", "±${(hitWindow.greatWindow / rate).roundBy(2)}ms", OsuColors.blue.toVerktex()),
                    Metric("OK hit window", "±${(hitWindow.okWindow / rate).roundBy(2)}ms", OsuColors.green.toVerktex()),
                    Metric("MEH hit window", "±${(hitWindow.mehWindow / rate).roundBy(2)}ms", OsuColors.yellow.toVerktex()),
                    Metric("MISS hit window", "±${(HitWindow.MISS_WINDOW / rate).roundBy(2)}ms", OsuColors.red.toVerktex()),
                    Metric("RPM required to clear spinners", (2 + 12 * nonRateAdjustedDifficulty.od).roundToInt().toString())
                ),
                if (isPrecise) arrayOf("Hit windows are being adjusted by the Precise mod.") else emptyArray()
            )

            // HP

            addAttribute(
                "HP (Health Drain)",
                "Affects the harshness of health drain and the health penalties for missing.",
                difficulty.hp,
                rateAdjustedDifficulty.hp
            )
        }
    }


    fun show() {
        Verktex.withTreeLock {
            EngineRouter.showOverlay(this)
            modal.show()
        }
    }


    private fun addAttribute(name: String, description: String, originalValue: Float, adjustedValue: Float,
                             metrics: Array<Metric> = emptyArray(), additionalInfo: Array<String> = emptyArray()) {
        modalCard += UILinearContainer().apply {
            orientation = UILinearContainer.Orientation.Vertical

            +UILinearContainer().apply {
                orientation = UILinearContainer.Orientation.Vertical

                +text(name, "font") { it.palette.accent }
                +text(description, "smallFont") { it.palette.accent }
            }

            +UILinearContainer().apply {
                orientation = UILinearContainer.Orientation.Vertical

                metrics.forEach { metric ->
                    +text("${metric.name}: ${metric.value}", "smallFont") { metric.color ?: (it.palette.accent * 0.8f) }
                }
            }

            if (originalValue != adjustedValue || additionalInfo.isNotEmpty()) {
                +UILinearContainer().apply {
                    orientation = UILinearContainer.Orientation.Vertical

                    if (originalValue != adjustedValue) {
                        +text(
                            "This value is being adjusted by mods (${originalValue.roundBy(2)} ➜ ${adjustedValue.roundBy(2)}).",
                            "smallFont"
                        ) { it.palette.accent * 0.6f }
                    }

                    additionalInfo.forEach { info ->
                        +text(info, "smallFont") { it.palette.accent * 0.6f }
                    }
                }
            }
        }
    }

    // Verktex only passes the alpha down, so every text gets its colour explicitly.
    private fun text(value: String, font: String, colorOf: (Theme) -> Color4) = UIText().apply {
        text = value
        // The AndEngine texts did not wrap.
        wrapText = false
        style = Style { theme -> color = colorOf(theme) }
        texts += this to font
    }


    override fun onUpdate(clock: TickingClock) {
        if (appliedScale != LegacyUnits.scale) {
            appliedScale = LegacyUnits.scale

            modal.card.radius = 16f.vu.px
            modalCard.padding = Padding(20f.vu.px)
            modalCard.spacing = 25f.vu.px

            modalCard.forEach { attribute ->
                attribute as UILinearContainer
                attribute.spacing = 10f.vu.px
                (attribute[0] as UILinearContainer).spacing = 5f.vu.px
            }

            texts.forEach { (text, font) -> text.fontSize = LegacyUnits.fontSize(font).vu.px }
        }

        super.onUpdate(clock)
    }


    //region Layout

    // The overlay container keeps the safe area free, the AndEngine scene covered the whole
    // screen, so the display takes the whole window instead of the bounds it is given. This relies
    // on the overlay container sitting at the window origin.

    override fun onMeasure(parentConstraints: LayoutConstraints, contentConstraints: LayoutConstraints): Vec2 {
        val windowSize = windowSize()
        val childConstraints = LayoutConstraints(0f, windowSize.x, 0f, windowSize.y)

        forEach { it.measure(childConstraints) }

        return windowSize
    }

    override fun layout(assignedBounds: Vec4) {
        val windowSize = windowSize()

        measuredSize = windowSize
        super.layout(Vec4(0f, 0f, windowSize.x, windowSize.y))
    }

    private fun windowSize() = Vec2(
        Verktex.graphics.window.windowWidth.toFloat(),
        Verktex.graphics.window.windowHeight.toFloat()
    )

    //endregion

    //region Input

    override fun onInputEvent(event: InputEvent): Boolean {
        if (event is KeyboardEvent) {
            // The AndEngine child scene swallowed Back, Enter and Menu while it was set, hiding included.
            return event.key == KeyboardEvent.Key.Back
                || event.key == KeyboardEvent.Key.Enter
                || event.key == KeyboardEvent.Key.Menu
        }

        return super.onInputEvent(event)
    }

    //endregion


    private inner class BeatmapAttributeModal : UIModal(

        card = UIScrollableContainer().apply {
            scrollAxes = Axis.Y
            width = fill(0.8f)
            height = fill(0.75f)
            anchor = Anchor.Center
            origin = Anchor.Center
            // The AndEngine card given to the modal kept the default scale centre, its top-left corner.
            scaleOrigin = Anchor.TopLeft
            clipping = ClipMode.Bounds

            // The AndEngine UIModal coloured and rounded its card.
            style = Style { theme ->
                backgroundColor = theme.palette.accent * 0.15f
            }

            +UILinearContainer().apply {
                orientation = UILinearContainer.Orientation.Vertical
            }
        }

    ) {

        // The linear 0.2 s show and hide of the AndEngine UIModal, which replace the Verktex ones.
        private val alphaAnimation = FloatAnimation(0f, ANIMATION_DURATION, Easing.None)
        private val scaleAnimation = FloatAnimation(0.9f, ANIMATION_DURATION, Easing.None)
        private var animationElapsed = ANIMATION_DURATION

        init {
            backgroundColor = Color4.Black.copy(alpha = 0.3f)
        }

        override fun onShow() {
            super.onShow()
            alphaAnimation.target = 1f
            scaleAnimation.target = 1f
            animationElapsed = 0f
        }

        override fun onHide() {
            super.onHide()
            alphaAnimation.target = 0f
            scaleAnimation.target = 0.9f
            animationElapsed = 0f
        }

        override fun onShowAnimation(clock: TickingClock) = advanceAnimation(clock)

        override fun onHideAnimation(clock: TickingClock) = advanceAnimation(clock)

        // Both animations run for ANIMATION_DURATION from the same start.
        private fun advanceAnimation(clock: TickingClock): Boolean {
            animationElapsed += clock.elapsedTickTime

            alpha = alphaAnimation.update(clock)
            card.scale = scaleAnimation.update(clock)

            return animationElapsed >= ANIMATION_DURATION
        }

        override fun onHidden() {
            super.onHidden()
            EngineRouter.hideOverlay(this@BeatmapAttributeDisplay)
        }

        // The AndEngine UIModal hid on the first touch outside the card and kept taking touches until hidden.
        override fun onInputEvent(event: InputEvent): Boolean {
            if (!isShown) {
                // Cancel events are dispatched to every component, so they must not be consumed.
                return isVisible && event is PointerEvent && event.action != PointerEvent.Action.Unknown && event.action != PointerEvent.Action.Cancel
            }

            if (event is PointerEvent
                && event.action == PointerEvent.Action.Down
                && !staticBackdrop
                && !card.containsScenePoint(event.x, event.y)) {
                hide()
                return true
            }

            return super.onInputEvent(event)
        }
    }

    private data class Metric(val name: String, val value: String, val color: Color4? = null)


    companion object {
        private const val ANIMATION_DURATION = 0.2f
    }
}
