package com.osudroid.ui.verktex

import com.osudroid.mods.*
import com.osudroid.ui.v2.modmenu.ModIcon
import com.osudroid.ui.verktex.LegacyUnits.vu
import com.reco1l.verktex.data.px
import com.reco1l.verktex.time.TickingClock
import com.reco1l.verktex.ui.container.UILinearContainer

class ModsIndicator : UILinearContainer() {

    /**
     * Whether to show mods that are not playable. Defined by [Mod.isUserPlayable].
     */
    var showNonPlayableMods: Boolean = false
        set(value) {
            if (field != value) {
                field = value

                // Reapply mods to update visibility
                val previousMods = mods
                mods = null
                mods = previousMods
            }
        }

    /**
     * The list of mods to display.
     */
    var mods: Iterable<Mod>? = null
        set(value) {
            if (field != value) {
                field = value

                clearChildren()

                value?.forEach { mod ->
                    if (!showNonPlayableMods && !mod.isUserPlayable) {
                        return@forEach
                    }

                    +ModIcon(mod).apply {
                        width = iconSize.vu.px
                        height = iconSize.vu.px
                    }
                }
            }
        }

    /**
     * The size of the mod icons in this indicator.
     */
    var iconSize = 42f
        set(value) {
            if (field != value) {
                field = value

                forEach { icon ->
                    icon.width = value.vu.px
                    icon.height = value.vu.px
                }
            }
        }


    // LegacyUnits.scale the sizes were last applied for.
    private var appliedScale = 0f


    init {
        orientation = Orientation.Horizontal
        spacing = (-5f).vu.px
    }


    override fun onUpdate(clock: TickingClock) {
        // The virtual units follow the window size, which may change after the sizes were set.
        if (appliedScale != LegacyUnits.scale) {
            appliedScale = LegacyUnits.scale

            spacing = (-5f).vu.px

            forEach { icon ->
                icon.width = iconSize.vu.px
                icon.height = iconSize.vu.px
            }
        }

        super.onUpdate(clock)
    }

}
