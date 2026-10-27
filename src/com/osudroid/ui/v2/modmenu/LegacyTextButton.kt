package com.osudroid.ui.v2.modmenu

import com.osudroid.ui.verktex.LegacyUnits
import com.osudroid.ui.verktex.LegacyUnits.vu
import com.osudroid.ui.verktex.SkinTextures
import com.reco1l.verktex.data.Anchor
import com.reco1l.verktex.data.Color4
import com.reco1l.verktex.data.px
import com.reco1l.verktex.time.TickingClock
import com.reco1l.verktex.ui.UISprite
import com.reco1l.verktex.ui.container.UILinearContainer
import com.reco1l.verktex.ui.text.UIText

/**
 * The AndEngine UITextButton with a leading icon: the icon is sized to the text height and 8
 * apart from it, both centred in the button.
 */
open class LegacyTextButton(
    text: String,
    private val iconName: String,
    private val iconAssetPath: String
) : LegacyButton() {

    private val icon = UISprite()

    private val label = UIText().apply {
        this.text = text
        wrapText = false
    }

    private val row = UILinearContainer().apply {
        orientation = UILinearContainer.Orientation.Horizontal
        anchor = Anchor.Center
        origin = Anchor.Center

        +icon
        +label
    }

    private var shouldLoadTexture = true

    // The label height the icon was last sized to.
    private var iconSize = 0f

    init {
        +row
    }

    override fun onApplyLegacySizes() {
        row.spacing = 8f.vu.px
        label.fontSize = LegacyUnits.fontSize("smallFont").vu.px
    }

    override fun applyTint(tint: Color4) {
        icon.color = tint
        label.color = tint
    }

    override fun onUpdate(clock: TickingClock) {
        if (shouldLoadTexture) {
            shouldLoadTexture = false
            icon.textureRegion = SkinTextures.get(iconName, iconAssetPath)
        }

        // CompoundText sized its leading icon to the text height on both axes.
        if (iconSize != label.measuredSize.y) {
            iconSize = label.measuredSize.y
            icon.width = iconSize.px
            icon.height = iconSize.px
        }

        super.onUpdate(clock)
    }
}
