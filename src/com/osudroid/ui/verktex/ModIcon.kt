package com.osudroid.ui.verktex

import com.osudroid.mods.Mod
import com.osudroid.ui.verktex.LegacyUnits.vu
import com.osudroid.utils.allModsInstances
import com.reco1l.verktex.data.Anchor
import com.reco1l.verktex.data.Color4
import com.reco1l.verktex.data.Vec4
import com.reco1l.verktex.data.fill
import com.reco1l.verktex.data.px
import com.reco1l.verktex.time.TickingClock
import com.reco1l.verktex.ui.UISprite
import com.reco1l.verktex.ui.container.UIContainer
import com.reco1l.verktex.ui.text.UIText

/**
 * The icon for a mod in the mod menu.
 */
class ModIcon(val mod: Mod) : UIContainer(), ISkinnable {

    private var shouldUpdateTexture = true

    // LegacyUnits.scale the font size was last applied for.
    private var appliedScale = 0f

    constructor(acronym: String): this(allModsInstances.find { it.acronym.equals(acronym, ignoreCase = true) }!!)


    // Both children exist up front and setupContent() only switches between them, as the texture
    // lookup has to run on the update thread while the tree is mutated on the draw worker.
    private val sprite = UISprite().apply {
        width = fill()
        height = fill()
        isVisible = false
    }

    private val acronymText = UIText().apply {
        anchor = Anchor.Center
        origin = Anchor.Center
        text = mod.acronym
        wrapText = false
        isVisible = false
        style = Style { theme -> color = theme.palette.accent }
    }

    init {
        // Verktex only passes the alpha down to children, like inheritAncestorsColor = false.
        style = Style { theme ->
            val accent = theme.palette.accent
            backgroundColor = if (sprite.isVisible) Color4.Transparent else accent.copy(
                red = accent.red * 0.1f,
                green = accent.green * 0.1f,
                blue = accent.blue * 0.1f
            )
        }

        this += sprite
        this += acronymText
    }


    private fun setupContent() {
        val texture = SkinTextures.get(mod.iconTextureName)

        sprite.textureRegion = texture
        sprite.isVisible = texture != null
        acronymText.isVisible = texture == null
        style()

        shouldUpdateTexture = false
    }


    override fun onUpdate(clock: TickingClock) {
        if (shouldUpdateTexture) {
            setupContent()
        }

        if (appliedScale != LegacyUnits.scale) {
            appliedScale = LegacyUnits.scale
            acronymText.fontSize = LegacyUnits.fontSize("smallFont").vu.px
        }

        super.onUpdate(clock)
    }

    override fun onLayout(bounds: Vec4) {
        super.onLayout(bounds)

        val height = bounds.height

        if (acronymText.isVisible && acronymText.bounds.height > 0f) {
            acronymText.scale = height * 0.6f / acronymText.bounds.height
        }

        radius = (height * 0.2f).px
    }

    override fun onSkinChanged() {
        shouldUpdateTexture = true
    }
}
