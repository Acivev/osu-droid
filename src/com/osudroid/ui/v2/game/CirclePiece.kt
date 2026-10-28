package com.osudroid.ui.v2.game

import com.reco1l.andengine.*
import com.reco1l.andengine.container.*
import com.reco1l.andengine.sprite.*
import com.reco1l.andengine.text.UITextureText
import org.andengine.opengl.texture.region.TextureRegion
import com.reco1l.framework.*
import ru.nsu.ccfit.zuev.osu.*
import ru.nsu.ccfit.zuev.skins.*

open class CirclePiece(

    circleTexture: String,
    overlayTexture: String

) : UIContainer() {


    init {
        origin = Anchor.Center
    }


    private val circle = UISprite().also {

        it.origin = Anchor.Center
        it.anchor = Anchor.Center
        it.textureRegion = ResourceManager.getInstance().getTexture(circleTexture)

        attachChild(it)
    }

    private val overlay = UISprite().also {

        it.origin = Anchor.Center
        it.anchor = Anchor.Center
        it.textureRegion = ResourceManager.getInstance().getTexture(overlayTexture)

        attachChild(it)
    }


    fun setCircleColor(red: Float, green: Float, blue: Float) {
        circle.setColor(red, green, blue)
    }

    fun setCircleColor(color: Color4) {
        circle.color = color
    }

    fun setCircleTextureRegion(circleTexture: String) {
        circle.textureRegion = ResourceManager.getInstance().getTexture(circleTexture)
    }

    fun setOverlayTextureRegion(overlayTexture: String) {
        overlay.textureRegion = ResourceManager.getInstance().getTexture(overlayTexture)
    }
}

class NumberedCirclePiece(circleTexture: String, overlayTexture: String) : CirclePiece(circleTexture, overlayTexture) {


    // The AndEngine character map of SpriteFont, which moved to Verktex for the HUD; the playfield follows in 5c.
    private val number = UITextureText(mutableMapOf<Char, TextureRegion>().also { characters ->
        val prefix = OsuSkin.get().hitCirclePrefix

        for (i in 0..9) {
            characters['0' + i] = ResourceManager.getInstance().getTextureWithPrefix(prefix, i.toString())
        }

        characters['.'] = ResourceManager.getInstance().getTextureWithPrefix(prefix, "comma")
        characters['%'] = ResourceManager.getInstance().getTextureWithPrefix(prefix, "percent")
        characters['x'] = ResourceManager.getInstance().getTextureWithPrefix(prefix, "x")
        characters['d'] = ResourceManager.getInstance().getTextureWithPrefix(prefix, "d")
        characters['p'] = ResourceManager.getInstance().getTextureWithPrefix(prefix, "p")
    }).also {

        it.origin = Anchor.Center
        it.anchor = Anchor.Center
        it.spacing = -OsuSkin.get().hitCircleOverlap

        attachChild(it)
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