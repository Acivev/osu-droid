package com.reco1l.osu.ui.entity

import com.osudroid.multiplayer.api.data.WinCondition.Accuracy
import com.osudroid.multiplayer.Multiplayer
import com.osudroid.ui.verktex.LegacyUnits
import com.osudroid.ui.verktex.SkinTextures
import com.reco1l.verktex.data.Anchor
import com.reco1l.verktex.data.Color4
import com.reco1l.verktex.data.px
import com.reco1l.verktex.input.InputEvent
import com.reco1l.verktex.input.PointerEvent
import com.reco1l.verktex.ui.UIComponent
import com.reco1l.verktex.ui.UISprite
import com.reco1l.verktex.ui.container.UIContainer
import com.reco1l.verktex.ui.text.UIText
import org.andengine.util.math.MathUtils
import ru.nsu.ccfit.zuev.osu.scoring.StatisticV2
import java.text.NumberFormat.getNumberInstance
import java.util.Locale.ENGLISH
import java.util.Locale.US
import ru.nsu.ccfit.zuev.osu.GlobalManager.getInstance as getGlobal

/**
 * Scoreboard list used for multiplayer scores in results screen.
 */
class StatisticSelector(stats: Array<StatisticV2>?) : ScrollableList() {


    private var selected: StatisticV2? = getGlobal().scoring.currentStatistic
        set(value) {
            if (value != field && value != null) {
                field = value

                getGlobal().scoring.load(value, getGlobal().scoring.beatmapInfo, getGlobal().songService, null, null, null)
                getGlobal().scoring.show()
            }
        }


    init {
        stats?.forEachIndexed { i, stat -> addItem(i, stat) }
    }


    private fun addItem(index: Int, stats: StatisticV2) {
        camY = -146f

        val item = BoardItem(index, stats)
        val isSelected = stats.playerName == selected?.playerName

        if (stats.isAlive) {

            item.text.setColor(0.9f, 0.9f, 0.9f)
            item.rank.setColor(0.6f, 0.6f, 0.6f, 0.9f)

            if (isSelected) {
                item.setColor(0.5f, 0.5f, 1f, 0.9f)
            } else {
                item.setColor(0.5f, 0.5f, 0.5f, 0.9f)
            }

        } else {

            item.text.setColor(1f, 0.8f, 0.8f)
            item.rank.setColor(1f, 0.6f, 0.6f, 0.9f)

            if (isSelected) {
                item.setColor(1f, 0.4f, 0.4f, 0.9f)
            } else {
                item.setColor(1f, 0.6f, 0.6f, 0.9f)
            }

        }

        this += item

        itemHeight = 100f
    }


    // A container for the sprite and its texts, Verktex sprites have no children.
    inner class BoardItem(val index: Int, private val stats: StatisticV2) : UIContainer() {

        private var moved = false
        private var dx = 0f
        private var dy = 0f

        private val sprite = UISprite(SkinTextures.get("menu-button-background")).apply {
            scaleType = UISprite.ScaleType.Stretch
        }

        val text = UIText().apply {
            x = (10f * LegacyUnits.scale).px
            y = (15f * LegacyUnits.scale).px
            fontSize = (LegacyUnits.fontSize("font") * LegacyUnits.scale).px
            wrapText = false
        }

        val rank = UIText().apply {
            x = (10f * LegacyUnits.scale).px
            y = (15f * LegacyUnits.scale).px
            fontSize = (LegacyUnits.fontSize("CaptionFont") * LegacyUnits.scale).px
            wrapText = false
        }


        init {
            x = (570f * LegacyUnits.scale).px
            width = (140f * LegacyUnits.scale).px
            height = (100f * LegacyUnits.scale).px
            sprite.width = width
            sprite.height = height

            text.text = """
                ${stats.playerName}
                ${getNumberInstance(US).format(stats.totalScoreWithMultiplier)}
                ${
                when (Multiplayer.room!!.winCondition) {
                    Accuracy -> "%2.2f%%".format(ENGLISH, stats.accuracy * 100f)
                    else -> "${getNumberInstance(US).format(stats.scoreMaxCombo)}x"
                }
            }
            """.trimIndent()
            text.scaleOrigin = Anchor.TopLeft
            text.scale = 0.65f

            rank.text = "#${index + 1}"
            rank.scaleOrigin = Anchor.TopLeft
            rank.scale = 1.7f
            // The right edge at 100, as the unscaled width is only known once measured.
            rank.origin = Anchor.TopRight
            rank.x = (100f * LegacyUnits.scale).px
            rank.y = (30f * LegacyUnits.scale).px

            this += sprite
            this += rank
            this += text
        }


        // Tints the sprite only, the texts keep their own colours.
        fun setColor(red: Float, green: Float, blue: Float, alpha: Float) {
            sprite.color = Color4(red, green, blue, alpha)
        }


        override fun onInputEvent(event: InputEvent): Boolean {

            if (event !is PointerEvent) {
                return false
            }

            val local = toLocalSpaceFromScene(event.x, event.y)
            val localX = local.x / LegacyUnits.scale
            val localY = local.y / LegacyUnits.scale

            if (event.action == PointerEvent.Action.Down) {
                moved = false
                dx = localX
                dy = localY

                return true
            }

            if (event.action == PointerEvent.Action.Up) {
                velocityY = 0f

                if (moved || isScroll)
                    return false

                selected = stats
            }

            if (event.action == PointerEvent.Action.Move && MathUtils.distance(dx, dy, localX, localY) > 10) {
                moved = true
            }

            return false
        }
    }


    override fun onInputEvent(event: InputEvent): Boolean {
        // Only presses inside the touch area count, a pointer bound to an item is followed anywhere.
        if (event is PointerEvent && !isBound(event.pointerId) && !contains(event.x / LegacyUnits.scale, event.y / LegacyUnits.scale)) {
            return false
        }

        return super.onInputEvent(event)
    }

    fun contains(pX: Float, pY: Float): Boolean {
        return pX in 570f..570f + 140f
    }
}


private fun UIComponent.setColor(red: Float, green: Float, blue: Float, alpha: Float = 1f) {
    color = Color4(red, green, blue, alpha)
}
