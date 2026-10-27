package com.osudroid.ui

import com.osudroid.ui.verktex.LegacyUnits
import com.osudroid.ui.verktex.LegacyUnits.vu
import com.osudroid.ui.verktex.SkinTextures
import com.osudroid.utils.updateThread
import com.reco1l.verktex.Verktex
import com.reco1l.verktex.data.Anchor
import com.reco1l.verktex.data.Color4
import com.reco1l.verktex.data.Dimension
import com.reco1l.verktex.data.LayoutConstraints
import com.reco1l.verktex.data.fill
import com.reco1l.verktex.data.px
import com.reco1l.verktex.graphics.GraphicsTexture
import com.reco1l.verktex.input.InputEvent
import com.reco1l.verktex.input.PointerEvent
import com.reco1l.verktex.math.Easing
import com.reco1l.verktex.math.FloatAnimation
import com.reco1l.verktex.time.TickingClock
import com.reco1l.verktex.ui.UIComponent
import com.reco1l.verktex.ui.UISprite
import com.reco1l.verktex.ui.container.UIContainer
import com.reco1l.verktex.ui.text.UIText
import ru.nsu.ccfit.zuev.osu.Config
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.round

class SendingPanel(
    private val overallRank: Long,
    private val score: Long,
    private val accuracy: Float,
    private val pp: Float
) : UIContainer() {
    // The 800x200 panel. The container also holds the button below it, so its touches reach the button.
    private val background = LegacyRectangle(0f, 0f, 800f, 200f).also { this += it }
    private var legacyWidth by background::legacyWidth
    private var legacyHeight by background::legacyHeight

    private val overallCaptionText = legacyText("CaptionFont", "Overall Ranking").also {
        it.setPosition((legacyWidth - it.legacyWidth) / 2, legacyHeight / 5)
        this += it
    }

    private val minWidth = legacyWidth
    private val minHeight = legacyHeight
    private val columnGap = 10

    private var moveModifier: FloatAnimation? = null

    private val dismissButton = object : DismissButton() {
        override fun onInputEvent(event: InputEvent): Boolean {
            // Only touches inside the button count.
            if (event !is PointerEvent || !containsScenePoint(event.x, event.y)) {
                return false
            }

            if (canBeDismissed) {
                this@SendingPanel.let {
                    val offset = it.legacyHeight + legacyHeight
                    moveModifier = moveYModifier(0.5f * (it.legacyY + offset) / offset, it.legacyY, -offset)
                }

                canBeDismissed = false
                return true
            }

            return false
        }
    }.also {
        this += it
        it.setPosition((legacyWidth - it.legacyWidth) / 2, legacyHeight)
        it.setText("Sending...")
    }

    private val rowContainer = LegacyRectangle(0f, 0f, legacyWidth, legacyHeight * 0.8f).also {
        it.setColor(0f, 0f, 0f, 0f)
        this += it
    }

    private val mapRankColumn = Column("Map Rank").also { rowContainer += it }
    private val overallRankColumn = Column("Overall Rank").also { rowContainer += it }
    private val accuracyColumn = Column("Accuracy").also { rowContainer += it }
    private val scoreColumn = Column("Score").also { rowContainer += it }
    private val ppColumn = Column("Performance").also { rowContainer += it }

    init {
        background.setColor(0f, 0f, 0f, 0.7f)
        resetPosition()
    }

    override fun onUpdate(clock: TickingClock) {
        moveModifier?.let { setPosition(legacyX, it.update(clock)) }

        super.onUpdate(clock)
    }

    // Called from the submission thread. Nothing to do once the results screen dropped the panel.
    fun show(newMapRank: Long, newOverallRank: Long, newScore: Long, newAccuracy: Float, newPP: Float) {
        if (parent == null) {
            return
        }

        updateThread { Verktex.withTreeLock {
        if (parent == null) {
            return@withTreeLock
        }

        dismissButton.canBeDismissed = true

        // Map rank column is special, as its color is different when ranking up
        updateColumn(mapRankColumn, "#$newMapRank", 0f)
        if (newScore > score) {
            mapRankColumn.setValueRectColor(1f, 1f, 0f, 0.8f)
        }

        updateColumn(
            overallRankColumn,
            when {
                newOverallRank == overallRank -> "#$newOverallRank"
                newOverallRank < overallRank -> "#$newOverallRank\n(+${overallRank - newOverallRank})"
                else -> "#$newOverallRank\n(-${newOverallRank - overallRank})"
            },
            (overallRank - newOverallRank).toFloat(),
            mapRankColumn
        )

        updateColumn(
            accuracyColumn,
            when {
                abs(newAccuracy - accuracy) < 0.0001f -> String.format("%.2f%%", newAccuracy * 100f)
                newAccuracy < accuracy -> String.format("%.2f%%\n(%.2f%%)", newAccuracy * 100f, (newAccuracy - accuracy) * 100f)
                else -> String.format("%.2f%%\n(+%.2f%%)", newAccuracy * 100f, (newAccuracy - accuracy) * 100f)
            },
            newAccuracy - accuracy,
            overallRankColumn
        )

        updateColumn(
            scoreColumn,
            when {
                newScore == score -> formatScore(newScore)
                newScore < score -> "${formatScore(newScore)}\n(${formatScore(newScore - score)})"
                else -> "${formatScore(newScore)}\n(+${formatScore(newScore - score)})"
            },
            (newScore - score).toFloat(),
            accuracyColumn
        )

        updateColumn(
            ppColumn,
            // For PP, we only want to show significant changes.
            when {
                round(newPP) - round(pp) < 1 -> String.format("%.0fpp", round(newPP))
                round(newPP) - round(pp) < 0 -> String.format("%.0fpp\n(%.0fpp)", round(newPP), round(newPP) - round(pp))
                else -> String.format("%.0fpp\n(+%.0fpp)", round(newPP), round(newPP) - round(pp))
            },
            round(newPP - pp),
            scoreColumn
        )

        // Obtain the maximum height of all columns
        val columnMaxHeight = maxOf(
            mapRankColumn.legacyHeight,
            overallRankColumn.legacyHeight,
            accuracyColumn.legacyHeight,
            scoreColumn.legacyHeight,
            ppColumn.legacyHeight
        )

        // Update the height of all columns to the maximum height
        mapRankColumn.legacyHeight = columnMaxHeight
        overallRankColumn.legacyHeight = columnMaxHeight
        accuracyColumn.legacyHeight = columnMaxHeight
        scoreColumn.legacyHeight = columnMaxHeight
        ppColumn.legacyHeight = columnMaxHeight

        rowContainer.let {
            it.legacyWidth = ppColumn.legacyX + ppColumn.legacyWidth - mapRankColumn.legacyX
            it.legacyHeight = columnMaxHeight
        }

        legacyWidth = max(minWidth, max(overallCaptionText.legacyWidth, rowContainer.legacyWidth) + 20)
        legacyHeight = max(minHeight, minHeight / 5 + overallCaptionText.legacyHeight + rowContainer.legacyHeight + 10)

        rowContainer.let {
            it.setPosition((legacyWidth - it.legacyWidth) / 2, legacyHeight - it.legacyHeight - 10)
        }

        resetPosition()

        overallCaptionText.setPosition((legacyWidth - overallCaptionText.legacyWidth) / 2, (legacyHeight - overallCaptionText.legacyHeight) / 5)

        dismissButton.let {
            it.setText("Dismiss")
            it.setPosition((legacyWidth - it.legacyWidth) / 2, legacyHeight)
        }

        moveModifier = moveYModifier(0.5f, -legacyHeight, 0f)
        } }
    }

    // Called from the submission thread, see show().
    fun setFail() {
        if (parent == null) {
            return
        }

        updateThread { Verktex.withTreeLock {
        if (parent == null) {
            return@withTreeLock
        }

        dismissButton.setText("Failed")
        } }
    }

    private fun resetPosition() {
        setPosition((Config.getRES_WIDTH() - legacyWidth) / 2f, -legacyHeight)
    }

    private fun updateColumn(column: Column, value: String, difference: Float, prevColumn: Column? = null) = column.apply {
        updateValue(value)

        when {
            difference > 0 -> setValueRectColor(0f, 1f, 0f, 0.5f)
            difference < 0 -> setValueRectColor(1f, 0f, 0f, 0.5f)
            else -> setValueRectColor(0.4f, 0.4f, 0.4f, 0.8f)
        }

        setPosition(if (prevColumn != null) prevColumn.legacyX + prevColumn.legacyWidth + columnGap else 0f, 0f)
    }

    companion object {
        internal fun formatScore(score: Long) = score
            .toString()
            // Reverse the order
            .reversed()
            // Split into 3 characters each
            .chunked(3)
            // Join them
            .joinToString(" ") { it }
            // Reverse back to maintain original order
            .reversed()
    }

    private open class DismissButton(
        texture: GraphicsTexture.Region? = SkinTextures.getAsset("ranking_button", "ranking_button.png")
    ) : LegacyRectangle(0f, 0f, texture?.width ?: 0f, texture?.height ?: 0f) {
        var canBeDismissed = false

        private val sprite = UISprite(texture).also {
            it.scaleType = UISprite.ScaleType.Stretch
            it.width = fill()
            it.height = fill()
            this += it
        }

        private val text = legacyText("font", "").also {
            this += it
        }

        fun setText(text: String) {
            this.text.text = text

            updateTextPosition()
        }

        private fun updateTextPosition() = text.let {
            it.setPosition(
                (legacyWidth - it.legacyWidth) / 2,
                // For whatever reason, the text is not centered vertically,
                // so we need to manually adjust it by subtracting 10...
                (legacyHeight - it.legacyHeight) / 2 - 10
            )
        }
    }

    private class Column(caption: String) : LegacyRectangle(0f, 0f, 100f, 80f) {
        private val minWidth = legacyWidth
        private val minHeight = legacyHeight

        private val captionText = legacyText("font", caption).also {
            this += it
        }

        private val valueRect = LegacyRectangle(0f, 80f, legacyWidth, 0f).also {
            it.setColor(0.4f, 0.4f, 0.4f, 0.8f)
            this += it
        }

        private val valueText = legacyText("font", "").also {
            it.alignment = Anchor.TopCenter
            valueRect += it
        }

        init { setColor(0f, 0f, 0f, 0f) }

        fun updateValue(value: String) {
            valueText.text = value

            updateSizes()
        }

        fun setValueRectColor(pRed: Float, pGreen: Float, pBlue: Float, pAlpha: Float = 1f) {
            valueRect.setColor(pRed, pGreen, pBlue, pAlpha)
        }

        override var legacyWidth: Float
            get() = super.legacyWidth
            set(pWidth) {
            super.legacyWidth = pWidth

            // Center the caption text
            captionText.let { it.setPosition((legacyWidth - it.legacyWidth) / 2, 0f) }

            // Update the value rectangle width
            valueRect.let {
                it.legacyWidth = legacyWidth
                it.setPosition(0f, legacyHeight - it.legacyHeight)
            }

            // Center the value text
            valueText.let {
                it.setPosition((valueRect.legacyWidth - it.legacyWidth) / 2, (valueRect.legacyHeight - it.legacyHeight) / 2)
            }
        }

        override var legacyHeight: Float
            get() = super.legacyHeight
            set(pHeight) {
            super.legacyHeight = pHeight

            // Update the value rectangle height
            valueRect.let {
                it.legacyHeight = legacyHeight - captionText.legacyY - captionText.legacyHeight
                it.setPosition(0f, legacyHeight - it.legacyHeight)
            }

            // Center the value text
            valueText.let {
                it.setPosition((valueRect.legacyWidth - it.legacyWidth) / 2, (valueRect.legacyHeight - it.legacyHeight) / 2)
            }
        }

        private fun updateSizes() {
            legacyWidth = max(minWidth, max(captionText.legacyWidth + 10, valueText.legacyWidth + 20))
            legacyHeight = max(minHeight, captionText.legacyHeight + valueText.legacyHeight)
        }
    }
}


/**
 * A rectangle placed and sized in virtual units, applied in window pixels,
 * its colour being the background colour.
 */
private open class LegacyRectangle(x: Float, y: Float, width: Float, height: Float) : UIContainer() {
    open var legacyWidth = width
        set(value) {
            field = value
            this.width = value.vu.px
        }

    open var legacyHeight = height
        set(value) {
            field = value
            this.height = value.vu.px
        }

    init {
        setPosition(x, y)
        this.width = width.vu.px
        this.height = height.vu.px
    }

    fun setColor(red: Float, green: Float, blue: Float, alpha: Float = 1f) {
        backgroundColor = Color4(red, green, blue, alpha)
    }
}

// The size of a text, measured on demand.
private val unbounded = LayoutConstraints(0f, Float.POSITIVE_INFINITY, 0f, Float.POSITIVE_INFINITY)

private val UIText.legacyWidth: Float
    get() = measure(unbounded).x / LegacyUnits.scale

private val UIText.legacyHeight: Float
    get() = measure(unbounded).y / LegacyUnits.scale

private fun legacyText(fontName: String, text: String) = UIText().also {
    it.fontSize = LegacyUnits.fontSize(fontName).vu.px
    it.wrapText = false
    it.color = Color4.White
    it.text = text
}

private val UIComponent.legacyX: Float
    get() = (x as Dimension.Fixed).toPixels() / LegacyUnits.scale

private val UIComponent.legacyY: Float
    get() = (y as Dimension.Fixed).toPixels() / LegacyUnits.scale

private fun UIComponent.setPosition(x: Float, y: Float) {
    this.x = x.vu.px
    this.y = y.vu.px
}

// A linear move of the y position from fromY to toY over duration seconds, applied in onUpdate.
private fun moveYModifier(duration: Float, fromY: Float, toY: Float) = FloatAnimation(fromY, 0f, Easing.None).also {
    it.animateTo(toY, duration, Easing.None)
}
