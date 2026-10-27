package com.osudroid.ui.v2.modmenu

import com.osudroid.mods.*
import com.osudroid.multiplayer.*
import com.osudroid.ui.verktex.LegacyUnits
import com.osudroid.ui.verktex.LegacyUnits.vu
// The explicit import picks the Verktex twin over the same-package AndEngine ModIcon.
import com.osudroid.ui.verktex.ModIcon
import com.osudroid.utils.searchContiguously
import com.reco1l.verktex.data.Anchor
import com.reco1l.verktex.data.Axis
import com.reco1l.verktex.data.ClipMode
import com.reco1l.verktex.data.Color4
import com.reco1l.verktex.data.Padding
import com.reco1l.verktex.data.fill
import com.reco1l.verktex.data.px
import com.reco1l.verktex.math.Easing
import com.reco1l.verktex.math.FloatAnimation
import com.reco1l.verktex.time.TickingClock
import com.reco1l.verktex.ui.container.UIContainer
import com.reco1l.verktex.ui.container.UILinearContainer
import com.reco1l.verktex.ui.text.UIText
import ru.nsu.ccfit.zuev.osu.*

class ModMenuToggle(var mod: Mod): LegacyButton() {

    /**
     * Whether the [Mod] represented by this [ModMenuToggle] is incompatible with one or more enabled [Mod]s.
     */
    var hasIncompatibility = false
        set(value) {
            if (field != value) {
                field = value
                applyCompatibilityState()
            }
        }

    private val icon = ModIcon(mod).apply {
        anchor = Anchor.CenterLeft
        origin = Anchor.CenterLeft
    }

    private val nameText = UIText().apply {
        text = mod.name
        wrapText = false
    }

    private val descriptionText = UIText().apply {
        width = fill()
        text = mod.description
        wrapText = false
        clipping = ClipMode.Bounds
        // The AndEngine UIText scrolled overflowing text horizontally by default.
        autoScrollAxes = Axis.X
    }

    // Verktex linear containers place children at the top of the row, so the texts are centred in
    // a plain container and start after the icon width plus the spacing.
    private val textColumn = UILinearContainer().apply {
        orientation = UILinearContainer.Orientation.Vertical
        width = fill()
        anchor = Anchor.CenterLeft
        origin = Anchor.CenterLeft

        +nameText
        +descriptionText
    }

    // Fades to 0.5 while incompatible, on top of the enabled fade of LegacyButton.
    private val compatibilityAlpha = FloatAnimation(1f, 0.2f, Easing.None)

    init {
        width = fill()

        +UIContainer().apply {
            width = fill()

            +icon
            +textColumn
        }

        onActionUp = {
            if (isSelected) {
                ModMenu.removeMod(mod)
                ResourceManager.getInstance().getSound("check-off")?.play()
            } else {
                ModMenu.addMod(mod)
                ResourceManager.getInstance().getSound("check-on")?.play()
            }
        }

        updateVisibility()
    }

    @JvmOverloads
    fun updateVisibility(searchTerm: String = "") {
        var shouldBeVisible = if (Multiplayer.isMultiplayer) {
            // isValidForMultiplayer is always checked, even if the room hasn't been received yet,
            // to avoid invalid mods (e.g. Autoplay) appearing before room data arrives.
            mod.isValidForMultiplayer && (Multiplayer.room == null || Multiplayer.isRoomHost ||
                    (Multiplayer.room!!.gameplaySettings.isFreeMod && mod.isValidForMultiplayerAsFreeMod))
        } else {
            true
        }

        if (searchTerm.isNotBlank()) {
            shouldBeVisible = shouldBeVisible &&
                (mod.acronym.equals(searchTerm, true) ||
                    mod.name.searchContiguously(searchTerm, true))
        }

        isVisible = shouldBeVisible
    }

    fun applyCompatibilityState() {
        // Intentionally not using isEnabled here, otherwise the button will not be clickable.
        compatibilityAlpha.target = if (hasIncompatibility) 0.5f else 1f
    }

    override fun onApplyLegacySizes() {
        icon.width = 38f.vu.px
        icon.height = 38f.vu.px
        textColumn.padding = Padding(left = (38f + 8f).vu.px)
        nameText.fontSize = LegacyUnits.fontSize("smallFont").vu.px
        descriptionText.fontSize = LegacyUnits.fontSize("xs").vu.px
        descriptionText.autoScrollSpeed = 15f.vu
    }

    // AndEngine tinted the texts with the button colour, Verktex only passes the alpha down.
    override fun applyTint(tint: Color4) {
        nameText.color = tint
        descriptionText.color = tint.copy(alpha = 0.75f)
    }

    override fun updateAlphaFactor(clock: TickingClock) = compatibilityAlpha.update(clock)

}
