package com.osudroid.ui.v2.modmenu

import com.osudroid.ui.verktex.LegacyUnits
import com.osudroid.ui.verktex.LegacyUnits.vu
import com.osudroid.ui.verktex.ModsIndicator
import com.reco1l.verktex.data.Padding
import com.reco1l.verktex.data.fill
import com.reco1l.verktex.data.px
import com.reco1l.verktex.time.TickingClock
import com.reco1l.verktex.ui.UITextButton
import com.reco1l.verktex.ui.container.UILinearContainer
import com.reco1l.verktex.ui.dialog.UIDialog
import com.reco1l.verktex.ui.form.FormInput

class ModPresetsForm(section: ModMenuPresetsSection) : UIDialog<UILinearContainer>(innerContent = UILinearContainer().apply {
    orientation = UILinearContainer.Orientation.Vertical
    width = fill()
}) {

    private val modsIndicator = ModsIndicator().apply {
        mods = ModMenu.enabledMods.values
    }

    // LegacyUnits.scale the sizes were last applied for.
    private var appliedScale = 0f


    init {
        title = "New mod preset"
        staticBackdrop = true

        val nameInput = FormInput().apply {
            label = "Name"
            width = fill()
            showResetButton = false
        }

        innerContent.apply {
            +nameInput
            +modsIndicator
        }

        addButton(UITextButton().apply {
            text = "Save"
            isSelected = true
            onActionUp = {
                section.saveModPreset(nameInput.value)
                hide()
            }
        })

        addButton(UITextButton().apply {
            text = "Cancel"
            onActionUp = {
                hide()
            }
        })
    }


    override fun onUpdate(clock: TickingClock) {
        if (appliedScale != LegacyUnits.scale) {
            appliedScale = LegacyUnits.scale

            modsIndicator.padding = Padding(24f.vu.px, 24f.vu.px, 24f.vu.px, 12f.vu.px)
        }

        super.onUpdate(clock)
    }
}
