package com.osudroid.ui.v2.hud.elements

import com.edlplan.framework.easing.*
import com.osudroid.ui.v2.hud.GameplayLayer
import com.osudroid.ui.verktex.LegacyUnits.vu
import com.osudroid.ui.verktex.SkinTextures
import com.osudroid.ui.verktex.toVerktex
import com.reco1l.verktex.data.Anchor
import com.reco1l.verktex.data.px
import com.reco1l.verktex.graphics.BlendMode
import com.reco1l.verktex.graphics.CompareFunction
import com.reco1l.verktex.graphics.DepthState
import com.reco1l.verktex.graphics.GraphicsRenderer
import com.reco1l.verktex.graphics.GraphicsTexture
import com.reco1l.verktex.ui.UIAnimatedSprite
import com.reco1l.verktex.ui.UISprite
import com.reco1l.verktex.ui.shape.UIBox
import com.reco1l.framework.*
import com.osudroid.ui.v2.hud.HUDElement
import com.rian.andengine.modifier.clearModifiers
import com.rian.andengine.modifier.fadeOut
import com.rian.andengine.modifier.scaleTo
import ru.nsu.ccfit.zuev.osu.*
import ru.nsu.ccfit.zuev.osu.game.GameScene
import ru.nsu.ccfit.zuev.osu.scoring.*
import ru.nsu.ccfit.zuev.skins.*

class HUDHealthBar : HUDElement() {

    private val fill: UIAnimatedSprite
    private val fillClear: UIBox

    private val marker: UISprite
    private val explode: UISprite

    private val markerNormalTexture: GraphicsTexture.Region?
    private val markerDangerTexture: GraphicsTexture.Region?
    private val markerSuperDangerTexture: GraphicsTexture.Region?

    private val isNewStyle: Boolean


    private var lastHP = 0f

    // The AndEngine floats of the fill and the clear box, in virtual units.
    private val fillX: Float
    private val fillY: Float
    private val fillWidth: Float
    private val fillHeight: Float
    private var fillClearWidth = 0f


    init {

        val backgroundTexture = SkinTextures.getWithBeatmapSkin("scorebar-bg")
        val markerTexture = optionalTexture("scorebar-marker")

        // the marker lookup to decide which display style must be performed on the source of the bg, which is the most common element.
        isNewStyle = backgroundTexture != null && markerTexture != null

        // background implementation is the same for both versions.
        this += UISprite().apply { setLegacyTexture(backgroundTexture) }

        fillClear = DepthBox()
        fillClear.origin = Anchor.TopRight
        fillClear.clearDepthBeforeDraw = true
        fillClear.depthState = DEPTH_TEST_LESS
        fillClear.alpha = 0f
        this += fillClear

        fill = UIAnimatedSprite(GameplayLayer.skinAnimationFrames("scorebar-colour"), OsuSkin.get().animationFramerate)
        fill.scaleType = UISprite.ScaleType.Stretch
        fill.depthState = DEPTH_TEST_LESS
        // Preserve the first frame size.
        fillWidth = fill.textureRegion?.width ?: 0f
        fillHeight = fill.textureRegion?.height ?: 0f
        fill.width = fillWidth.vu.px
        fill.height = fillHeight.vu.px
        this += fill

        marker = UISprite()
        marker.origin = Anchor.Center
        marker.blendMode = BlendMode.Additive
        this += marker

        explode = UISprite()
        explode.origin = Anchor.Center
        explode.blendMode = BlendMode.Additive
        explode.alpha = 0f
        this += explode

        if (isNewStyle) {
            fillX = 7.5f * 1.6f
            fillY = 7.8f * 1.6f

            marker.setLegacyTexture(markerTexture)
            explode.setLegacyTexture(markerTexture)

            markerNormalTexture = null
            markerDangerTexture = null
            markerSuperDangerTexture = null
        } else {
            fillX = 3f * 1.6f
            fillY = 10f * 1.6f

            markerNormalTexture = optionalTexture("scorebar-ki")
            markerDangerTexture = optionalTexture("scorebar-kidanger")
            // The skin's "scorebar-kidanger" stands in for a missing "scorebar-kidanger2".
            markerSuperDangerTexture = optionalTexture("scorebar-kidanger2") ?: markerDangerTexture

            marker.setLegacyTexture(markerNormalTexture)
            explode.setLegacyTexture(markerNormalTexture)
        }
        fill.x = fillX.vu.px
        fill.y = fillY.vu.px

        fillClearWidth = 0f
        fillClear.width = 0f.px
        fillClear.height = fillHeight.vu.px
        fillClear.x = (fillX + fillWidth).vu.px
        fillClear.y = fillY.vu.px
    }


    override fun onGameplayUpdate(game: GameScene, secondsElapsed: Float) {
        val hp = game.stat.hp

        fillClearWidth = Interpolation.floatAt(secondsElapsed.coerceIn(0f, 0.2f), fillClearWidth, (1f - hp) * fillWidth, 0f, 0.2f, Easing.OutQuint)
        fillClear.width = fillClearWidth.vu.px

        marker.x = (fillX + fillWidth - fillClearWidth).vu.px
        marker.y = (fillY + (if (isNewStyle) fillHeight / 2 else 0f)).vu.px

        explode.x = marker.x
        explode.y = marker.y

        if (hp > lastHP) {
            bulge()
        }

        lastHP = hp

        if (isNewStyle) {

            val color = getFillColor(hp).toVerktex()

            fill.color = color
            marker.color = color
            marker.blendMode = if (hp < EPIC_CUTOFF) BlendMode.Alpha else BlendMode.Additive

        } else {

            marker.setLegacyTexture(when {
                hp < 0.2f -> markerSuperDangerTexture
                hp < EPIC_CUTOFF -> markerDangerTexture
                else -> markerNormalTexture
            })

        }
    }

    override fun onNoteHit(statistics: StatisticV2) {
        flash(statistics.hp)
    }

    override fun onBreakStateChange(isBreak: Boolean) {
        forEach { it.isVisible = !isBreak }
    }


    private fun flash(hp: Float) {

        val isEpic = hp >= EPIC_CUTOFF

        bulge()

        explode.clearModifiers()
        explode.blendMode = if (isEpic) BlendMode.Additive else BlendMode.Alpha
        explode.alpha = 1f
        explode.scale = 1f

        explode.scaleTo(if (isEpic) 2f else 1.6f, 0.12f)
        explode.fadeOut(0.12f)
    }

    private fun bulge() {
        marker.clearModifiers()
        marker.scale = 1.4f
        marker.scaleTo(1f, 0.2f, Easing.Out)
    }

    private fun getFillColor(hp: Float) = when {

        hp < 0.2f -> Colors.interpolateNonLinear(0.2f - hp, Color4.Black, Color4.Red, 0f, 0.2f)
        hp < EPIC_CUTOFF -> Colors.interpolateNonLinear(0.5f - hp, Color4.White, Color4.Black, 0f, 0.5f)

        else -> Color4.White
    }

    // A texture of the AndEngine optional set: the skin's file when the legacy store has it, else none.
    // getTextureIfLoaded read the user skin store only, never the beatmap skin.
    private fun optionalTexture(name: String) =
        if (ResourceManager.getInstance().isTextureLoaded(name)) SkinTextures.get(name) else null

    // The AndEngine sprite took its texture's size.
    private fun UISprite.setLegacyTexture(region: GraphicsTexture.Region?) {
        textureRegion = region
        scaleType = UISprite.ScaleType.Stretch
        width = (region?.width ?: 0f).vu.px
        height = (region?.height ?: 0f).vu.px
    }

    /**
     * A box that fills itself in its own draw, inside the canvas depth state. [UIBox] only fills its
     * background, which is drawn before the depth state applies.
     */
    private class DepthBox : UIBox() {
        override fun onDraw(renderer: GraphicsRenderer) {
            renderer.drawRect(0f, 0f, bounds.width, bounds.height, color = contentColor, radius = radius.toPixels())
        }
    }

    companion object {

        const val EPIC_CUTOFF = 0.5f

        // The AndEngine DepthInfo.Default and DepthInfo.Less: depth test and write with GL_LESS.
        private val DEPTH_TEST_LESS = DepthState(enabled = true, writeEnabled = true, function = CompareFunction.Less)

    }

}