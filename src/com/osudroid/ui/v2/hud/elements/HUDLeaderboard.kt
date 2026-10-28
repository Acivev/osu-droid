package com.osudroid.ui.v2.hud.elements

import com.osudroid.multiplayer.Multiplayer
import com.osudroid.ui.v2.hud.HUDElement
import com.osudroid.ui.verktex.LegacyUnits
import com.osudroid.ui.verktex.LegacyUnits.vu
import com.osudroid.ui.verktex.SkinTextures
import com.reco1l.verktex.data.Anchor
import com.reco1l.verktex.data.Color4
import com.reco1l.verktex.data.LayoutConstraints
import com.reco1l.verktex.data.Vec2
import com.reco1l.verktex.data.px
import com.reco1l.verktex.time.TickingClock
import com.reco1l.verktex.ui.UISprite
import com.reco1l.verktex.ui.container.UIContainer
import com.reco1l.verktex.ui.text.UIText
import kotlin.math.max
import ru.nsu.ccfit.zuev.osu.GlobalManager
import ru.nsu.ccfit.zuev.osu.menu.ScoreBoardItem
import ru.nsu.ccfit.zuev.osu.scoring.StatisticV2

class HUDLeaderboard : HUDElement() {

    var nextItems: List<ScoreBoardItem>? = null


    private var playerSprite: BoardItem? = null

    private var lastTimeDataChange = 0L


    private val stats: StatisticV2 = GlobalManager.getInstance().gameScene.stat

    private val replayId = GlobalManager.getInstance().scoring.replayID

    private val isReplaying = replayId != -1

    private val isGlobalLeaderboard = GlobalManager.getInstance().songMenu.isBoardOnline()


    override fun onMeasure(parentConstraints: LayoutConstraints, contentConstraints: LayoutConstraints): Vec2 {
        val size = super.onMeasure(parentConstraints, contentConstraints)

        // Setting a minimum size for the leaderboard, this is used to prevent the leaderboard
        // from being too small during HUD editor mode.
        return Vec2(max(size.x, SPRITE_WIDTH.vu), max(size.y, SPRITE_HEIGHT.vu))
    }

    override fun onUpdate(clock: TickingClock) {
        super.onUpdate(clock)

        if (!Multiplayer.isMultiplayer) {
            val items = GlobalManager.getInstance().songMenu.getBoard()

            // We consider that if it's in replay mode the length should be the same, in case it's not then the
            // length should be +1 greater (because of the new score).
            if (items != null && children.size == 0) {
                nextItems = items
            }
        }

        val isInvalidated = nextItems != null

        if (isInvalidated) {
            val items = nextItems
            nextItems = null
            invalidate(items)
        }

        val spriteCount = children.size

        if (spriteCount == 0 || playerSprite == null) {
            return
        }

        val player = playerSprite!!

        player.apply {

            // Animating rank change
            val elapsed = (System.currentTimeMillis() - lastTimeDataChange) * 0.001f

            when {
                elapsed < 1 -> setColor(
                    1 + (r - 1) * elapsed,
                    1 + (g - 1) * elapsed,
                    1 + (b - 1) * elapsed,
                    1 + (a - 1) * elapsed
                )

                else -> setColor(r, g, b, a)
            }

            // Updating score data, we skip this on multiplayer because the data must be already updated at this point.
            if (!Multiplayer.isMultiplayer) {
                data.apply {

                    // Updating info only if needed.
                    if (playScore != stats.totalScoreWithMultiplier || maxCombo != stats.scoreMaxCombo || accuracy != stats.accuracy) {
                        playScore = stats.totalScoreWithMultiplier
                        maxCombo = stats.scoreMaxCombo
                        accuracy = stats.accuracy

                        updateInfo()
                    }
                }
            }
        }

        val lastPlayerPosition = children.indexOf(player)
        var playerPosition = lastPlayerPosition

        if (!Multiplayer.isMultiplayer) {

            var i = lastPlayerPosition - 1
            while (i >= 0) {
                val sprite = children[i] as BoardItem

                if (player.data.playScore >= sprite.data.playScore) {
                    player.data.rank = sprite.data.rank
                    sprite.data.rank++

                    playerPosition--

                    sprite.updateRank()
                    player.updateRank()

                    lastTimeDataChange = System.currentTimeMillis()
                }
                --i
            }
        }

        // Updating positions only if needed.
        if (playerPosition != lastPlayerPosition || isInvalidated) {

            if (playerPosition != lastPlayerPosition) {
                if (player in this) {
                    this -= player
                    add(playerPosition, player)
                }
            }

            val maxY = SPRITE_HEIGHT * (SCORE_COUNT - 1)

            if (playerPosition < SCORE_COUNT) {

                var i = 0
                while (i < spriteCount) {
                    val sprite = children[i] as BoardItem

                    sprite.setPosition(0f, if (i >= SCORE_COUNT) maxY else SPRITE_HEIGHT * i)
                    sprite.isVisible = i < SCORE_COUNT
                    ++i
                }

            } else {

                // Computing the bound from player position towards the limit of sprites that can be shown.
                val minBound: Int = playerPosition - SCORE_COUNT + 1

                var i = 0
                while (i < spriteCount) {

                    val sprite = children[i] as BoardItem
                    val isInBounds = i in minBound + 1..<playerPosition

                    // Showing only sprites that are between the bound index exclusive up to player position inclusive, the
                    // first sprite will always be shown so that's why the bound index is exclusive.
                    sprite.isVisible = i == 0 || i == playerPosition || isInBounds

                    sprite.setPosition(0f, when {

                        // First always on top
                        i == 0 -> 0f

                        // Player always on bottom
                        i == playerPosition -> maxY

                        // Sprites outside the bounds will be placed at its respective limit, at this point this sprite
                        // shouldn't be visible.
                        !isInBounds -> if (i < minBound) 0f else maxY

                        // Placing sprites respectively from maxY accounting for first sprite
                        else -> maxY - SPRITE_HEIGHT * (playerPosition - i)
                    })
                    i++
                }
            }
        }
    }


    private fun invalidate(items: List<ScoreBoardItem>?) {

        clearChildren()

        var list: List<ScoreBoardItem> = items ?: emptyList()

        fun appendNewItem() = ScoreBoardItem().apply {

            userName = stats.playerName

            // Setting the initial rank as the last rank, in local leaderboard it'll always be the last index because
            // it's based on the local database. In online the server database provides up to 50 scores, so we can't know
            // the actual last rank of it unless the provided leaderboard size is lower than 50.
            if (!isGlobalLeaderboard || items.isNullOrEmpty() || items.size < 50)
                rank = list.size + 1

            list = list + this
        }

        val playerItem = when {

            // In replay mode we try to find the corresponding data according to the replay ID, once we find it we append
            // it to the last index as empty score.
            isReplaying -> {
                if (list.isNotEmpty()) {
                    list = list.mapNotNull { if (it.scoreId == replayId) null else it.clone() }

                    // Reordering ranks according to indexes.
                    list.forEachIndexed { i, it -> it.rank = i + 1 }
                }

                // Setting replay data rank as empty score and shifting the rest of scores, it will eventually set back
                // to the corresponding values.
                appendNewItem()
            }

            // In multiplayer, we try to find the corresponding data according to the username.
            Multiplayer.isMultiplayer -> list.find { it.userName == stats.playerName }

            // In solo we just append a new data.
            else -> {
                if (list.isNotEmpty()) {
                    list = list.map { it.clone() }
                }

                appendNewItem()
            }
        }

        var i = list.size - 1
        while (i >= 0) {
            val it = list[i]
            val sprite = BoardItem(it)

            if (it == playerItem) {

                // This is only used for multiplayer when the list gets invalidated we try to figure if the rank was
                // changed by referencing the old player sprite.
                if (playerSprite?.data?.rank != it.rank) {
                    lastTimeDataChange = System.currentTimeMillis()
                }

                // Determining if the player 'isAlive' was changed from the last time the leaderboard was invalidated,
                // this is only used for multiplayer.
                if (playerSprite?.data?.isAlive != it.isAlive) {
                    lastTimeDataChange = System.currentTimeMillis()
                }

                playerSprite = sprite
            }

            sprite.updateColors()
            addToBack(sprite)
            --i
        }
    }


    // The AndEngine sprite, whose color did not reach its texts; here the color is the background's.
    private inner class BoardItem(val data: ScoreBoardItem) : UIContainer() {

        private val backgroundSprite = UISprite(SkinTextures.getWithBeatmapSkin("menu-button-background")).apply {
            scaleType = UISprite.ScaleType.Stretch
            width = SPRITE_WIDTH.vu.px
            height = SPRITE_HEIGHT.vu.px
        }

        val info: UIText

        val rank: UIText

        // Storing target values, this is used when animating color changes.
        var r = 0.5f
        var g = 0.5f
        var b = 0.5f
        var a = 0.5f

        init {
            isVisible = false
            height = SPRITE_HEIGHT.vu.px
            width = SPRITE_WIDTH.vu.px

            // The AndEngine texts used the plain alpha blend function, the Verktex default.
            info = UIText()
            info.fontSize = LegacyUnits.fontSize("font").vu.px
            info.wrapText = false
            info.x = 10f.vu.px
            info.y = 15f.vu.px
            info.scaleOrigin = Anchor.TopLeft
            info.scale = 0.65f

            rank = UIText()
            rank.fontSize = LegacyUnits.fontSize("CaptionFont").vu.px
            rank.wrapText = false
            // The AndEngine x was 100 minus the unscaled width.
            rank.origin = Anchor.TopRight
            rank.x = 100f.vu.px
            rank.y = 30f.vu.px
            rank.scaleOrigin = Anchor.TopLeft
            rank.scale = 1.7f

            this += backgroundSprite
            this += rank
            this += info

            updateInfo()
            updateRank()
        }

        fun updateInfo() {
            info.text = data.get()
            info.scaleOrigin = Anchor.TopLeft
            info.scale = 0.65f
        }

        fun updateRank() {
            rank.text = if (data.rank == -1) "#?" else "#${data.rank}"
        }

        fun setPosition(x: Float, y: Float) {
            this.x = x.vu.px
            this.y = y.vu.px
        }

        fun setColor(r: Float, g: Float, b: Float, a: Float = backgroundSprite.alpha) {
            backgroundSprite.color = Color4(r, g, b, a)
        }

        fun updateColors() {
            r = 0.5f
            g = 0.5f
            b = 0.5f
            a = 0.5f

            if (data.isAlive || !Multiplayer.isMultiplayer) {
                val isOwnScore = !Multiplayer.isMultiplayer && isGlobalLeaderboard && data.userName == stats.playerName

                info.color = Color4(0.9f, 0.9f, 0.9f)
                rank.color = Color4(0.6f, 0.6f, 0.6f, 0.9f)

                if (playerSprite == this) b = 1f else {
                    if (isOwnScore)
                        r = 1f

                    setColor(r, g, b, a)
                }
                return
            }

            rank.color = Color4(1f, 0.6f, 0.6f, 0.9f)
            info.color = Color4(1f, 0.8f, 0.8f)
            a = 0.25f

            if (playerSprite == this) {
                r = 1f
                g = 0.4f
                b = 0.4f
            } else {
                r = 1f
                g = 0.6f
                b = 0.6f

                setColor(r, g, b, a)
            }
        }
    }


    companion object {
        private const val SPRITE_HEIGHT = 90f
        private const val SPRITE_WIDTH = 130f
        private const val SCORE_COUNT = 5
    }
}