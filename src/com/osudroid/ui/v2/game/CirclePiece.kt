package com.osudroid.ui.v2.game

import com.osudroid.ui.v2.SpriteFont
import com.osudroid.ui.verktex.SkinTextures
import com.osudroid.ui.verktex.toVerktex
import com.reco1l.verktex.data.Anchor
import com.reco1l.verktex.ui.UISprite
import com.reco1l.verktex.ui.container.UIContainer
import com.reco1l.framework.*
import ru.nsu.ccfit.zuev.skins.*

open class CirclePiece(

    circleTexture: String,
    overlayTexture: String

) : UIContainer() {


    init {
        origin = Anchor.Center
        // reco1l passed a container's colour down to its children, the dim-hit-objects tint animates it.
        tintChildren = true
    }


    private val circle = UISprite().also {

        it.origin = Anchor.Center
        it.anchor = Anchor.Center
        it.textureRegion = SkinTextures.getWithBeatmapSkin(circleTexture)

        this += it
    }

    private val overlay = UISprite().also {

        it.origin = Anchor.Center
        it.anchor = Anchor.Center
        it.textureRegion = SkinTextures.getWithBeatmapSkin(overlayTexture)

        this += it
    }


    fun setCircleColor(red: Float, green: Float, blue: Float) {
        circle.color = circle.color.copy(red = red, green = green, blue = blue)
    }

    fun setCircleColor(color: Color4) {
        circle.color = color.toVerktex()
    }

    fun setCircleTextureRegion(circleTexture: String) {
        circle.textureRegion = SkinTextures.getWithBeatmapSkin(circleTexture)
    }

    fun setOverlayTextureRegion(overlayTexture: String) {
        overlay.textureRegion = SkinTextures.getWithBeatmapSkin(overlayTexture)
    }
}

class NumberedCirclePiece(circleTexture: String, overlayTexture: String) : CirclePiece(circleTexture, overlayTexture) {


    private val number = SpriteFont(OsuSkin.get().hitCirclePrefix).also {

        it.origin = Anchor.Center
        it.anchor = Anchor.Center
        it.spacing = -OsuSkin.get().hitCircleOverlap

        this += it
    }


    fun setNumberText(value: Int) {
        number.text = value.toString()
    }

    fun setNumberScale(value: Float) {
        number.setTextureScale(value)
    }

    fun showNumber() {
        number.alpha = 1f
    }

    fun hideNumber() {
        number.alpha = 0f
    }

}