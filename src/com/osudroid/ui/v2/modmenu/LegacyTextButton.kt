package com.osudroid.ui.v2.modmenu

import androidx.annotation.StringRes
import com.osudroid.ui.verktex.LegacyUnits
import com.osudroid.ui.verktex.LegacyUnits.vu
import com.osudroid.ui.verktex.SkinTextures
import com.reco1l.verktex.data.Anchor
import com.reco1l.verktex.data.Color4
import com.reco1l.verktex.data.px
import com.reco1l.verktex.graphics.GraphicsTexture
import com.reco1l.verktex.time.TickingClock
import com.reco1l.verktex.ui.UISprite
import com.reco1l.verktex.ui.container.UIContainer
import com.reco1l.verktex.ui.container.UILinearContainer
import com.reco1l.verktex.ui.text.UIText
import ru.nsu.ccfit.zuev.osu.helper.StringTable

inline fun UIContainer.legacyTextButton(
    iconName: String?,
    iconAssetPath: String?,
    noinline loadIcon: (name: String, assetPath: String) -> GraphicsTexture.Region? = SkinTextures::get,
    builder: LegacyTextButton.() -> Unit
) = LegacyTextButton("", iconName, iconAssetPath, loadIcon).apply(builder).also { +it }

/**
 * The AndEngine UITextButton with a leading icon: the icon is sized to the text height and 8
 * apart from it, both centred in the button. [loadIcon] looks the icon up, skin first by default;
 * pass `SkinTextures::getAsset` for icons the legacy code loaded from the bundled assets only. A null
 * [iconName] makes a text-only button.
 */
open class LegacyTextButton(
    text: String,
    private var iconName: String?,
    private var iconAssetPath: String?,
    private var loadIcon: (name: String, assetPath: String) -> GraphicsTexture.Region? = SkinTextures::get
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

        if (iconName != null) {
            +icon
        }
        +label
    }

    var text by label::text

    private var shouldLoadTexture = true

    // The label height the icon was last sized to.
    private var iconSize = 0f

    init {
        +row
    }

    fun setText(@StringRes resId: Int) {
        text = StringTable.get(resId)
    }

    /**
     * Replaces the leading icon, it is looked up on the next update. A null [name] removes it.
     */
    fun setIcon(name: String?, assetPath: String?, loadIcon: (name: String, assetPath: String) -> GraphicsTexture.Region? = this.loadIcon) {
        iconName = name
        iconAssetPath = assetPath
        this.loadIcon = loadIcon
        shouldLoadTexture = true

        if (name == null) {
            if (icon in row) {
                row -= icon
            }
        } else if (icon !in row) {
            row -= label
            row += icon
            row += label
        }
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
            icon.textureRegion = iconName?.let { loadIcon(it, iconAssetPath!!) }
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
