package com.osudroid.ui.v2.hud.elements

import com.osudroid.ui.verktex.LegacyUnits.vu
import com.osudroid.ui.verktex.OutlineCircle
import com.reco1l.verktex.data.Anchor
import com.reco1l.verktex.data.Color4
import com.reco1l.verktex.data.fill
import com.reco1l.verktex.data.px
import com.reco1l.verktex.data.wrap
import com.reco1l.verktex.ui.shape.UIBox
import com.reco1l.verktex.ui.shape.UICircle
import com.osudroid.ui.v2.hud.HUDElement
import ru.nsu.ccfit.zuev.osu.Config
import ru.nsu.ccfit.zuev.osu.game.GameScene

sealed class HUDSongProgress : HUDElement() {
    abstract fun setProgress(progress: Float, isIntro: Boolean)

    override fun onGameplayUpdate(gameScene: GameScene, secondsElapsed: Float) {
        if (gameScene.elapsedTime < gameScene.firstObjectStartTime) {
            setProgress((gameScene.elapsedTime - gameScene.initialStartTime) / (gameScene.firstObjectStartTime - gameScene.initialStartTime), true)
        } else {
            setProgress((gameScene.elapsedTime - gameScene.firstObjectStartTime) / (gameScene.lastObjectEndTime - gameScene.firstObjectStartTime), false)
        }
    }
}

class HUDLinearSongProgress : HUDSongProgress() {

    private val backgroundRect = UIBox().apply {

        anchor = Anchor.BottomLeft
        origin = Anchor.BottomLeft
        width = fill()
        height = BAR_HEIGHT.vu.px

        backgroundColor = Color4.Black
        alpha = 0.3f
    }

    private val progressRect = UIBox().apply {

        anchor = Anchor.BottomLeft
        origin = Anchor.BottomLeft
        width = 0f.px
        height = BAR_HEIGHT.vu.px
        backgroundColor = Color4.White
        alpha = 0.4f
    }


    init {
        // Adding a 20epx padding so the user can grab the progress bar more easily.
        width = Config.getRES_WIDTH().toFloat().vu.px
        height = (BAR_HEIGHT + 20f).vu.px

        this += backgroundRect
        this += progressRect
    }


    override fun setProgress(progress: Float, isIntro: Boolean) {

        if (isIntro) {
            progressRect.backgroundColor = Color4(153f / 255f, 204f / 255f, 51f / 255f)
        } else {
            progressRect.backgroundColor = Color4(1f, 1f, 150f / 255f)
        }

        progressRect.width = (size.x * progress).vu.px
    }


    companion object {
        private const val BAR_HEIGHT = 7f
    }

}

class HUDPieSongProgress : HUDSongProgress() {

    private val circularProgress: UICircle


    init {
        width = wrap()
        height = wrap()

        // Reference: https://github.com/ppy/osu/blob/6455c0583b5e607baeca7f584410bc63515aa619/osu.Game/Skinning/LegacySongProgress.cs

        // Draw a white ring outline. Using PaintStyle.Outline avoids relying on the
        // depth buffer, which may not be available (DEPTH_SIZE = 0 in ConfigChooser).
        OutlineCircle().also { ring ->

            ring.width = 33f.vu.px
            ring.height = 33f.vu.px
            ring.anchor = Anchor.Center
            ring.origin = Anchor.Center
            ring.color = Color4.White
            // The AndEngine outline width was in window pixels.
            ring.lineWidth = 3f

            this += ring
        }

        circularProgress = UICircle().also { progress ->

            progress.width = 30f.vu.px
            progress.height = 30f.vu.px
            progress.anchor = Anchor.Center
            progress.origin = Anchor.Center
            progress.alpha = 0.6f

            this += progress
        }


        UICircle().also { dot ->

            dot.width = 4f.vu.px
            dot.height = 4f.vu.px
            dot.anchor = Anchor.Center
            dot.origin = Anchor.Center
            dot.color = Color4.White

            this += dot
        }
    }


    override fun setProgress(progress: Float, isIntro: Boolean) {

        if (isIntro) {
            circularProgress.color = circularProgress.color.copy(red = 199f / 255f, green = 1f, blue = 47f / 255f)
            circularProgress.setPortion(-1f + progress)
        } else {
            circularProgress.color = circularProgress.color.copy(red = 1f, green = 1f, blue = 1f)
            circularProgress.setPortion(progress)
        }

    }

}