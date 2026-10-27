package com.osudroid.ui.v2.modmenu

import com.osudroid.data.*
import com.osudroid.ui.v2.modmenu.ModMenu.addMod
import com.osudroid.ui.v2.modmenu.ModMenu.removeMod
import com.osudroid.ui.verktex.LegacyUnits
import com.osudroid.ui.verktex.LegacyUnits.vu
import com.osudroid.ui.verktex.ModsIndicator
import com.osudroid.utils.deserializeMods
import com.osudroid.utils.searchContiguously
import com.osudroid.utils.updateThread
import com.reco1l.verktex.Verktex
import com.reco1l.verktex.data.Color4
import com.reco1l.verktex.data.fill
import com.reco1l.verktex.data.px
import com.reco1l.verktex.ui.UITextButton
import com.reco1l.verktex.ui.container.UILinearContainer
import com.reco1l.verktex.ui.dialog.UIMessageDialog
import com.reco1l.verktex.ui.text.UIText

class ModMenuPresetsSection : ModMenuSection("Presets") {


    override val sectionWidth = 300f

    private val addButton: LegacyTextButton

    private var modPresets = listOf<ModPreset>()

    // The toggles of the last loadPresets(), they reach toggleContainer on the update thread.
    private var presetToggles = listOf<ModPresetToggle>()


    init {
        addButton = LegacyTextButton("Add preset", "plus", "plus.png").apply {
            width = fill()
            onActionUp = {
                ModPresetsForm(this@ModMenuPresetsSection).show()
            }
        }
        toggleContainer += addButton
    }


    fun saveModPreset(name: String) {

        if (name.isEmpty()) {
            return
        }

        val modPreset = ModPreset(
            name = name,
            serializedMods = ModMenu.enabledMods.serializeMods(includeIrrelevantMods = true)
        )

        DatabaseManager.modPresetTable.insert(modPreset)
        loadPresets()
        onModsChanged()
    }

    fun loadPresets() {
        val previousToggles = presetToggles

        modPresets = DatabaseManager.modPresetTable.getAll()

        presetToggles = modPresets.map { preset ->
            preset.mods = deserializeMods(preset.serializedMods)
            ModPresetToggle(preset)
        }

        val newToggles = presetToggles

        // ModMenu loads the presets when it is first created, which may be off the update thread.
        updateThread {
            Verktex.withTreeLock {
                previousToggles.forEach { toggleContainer -= it }
                newToggles.forEach { toggleContainer += it }
            }
        }
    }


    fun onModsChanged() {
        val enabledMods = ModMenu.enabledMods

        addButton.isEnabled = enabledMods.isNotEmpty()

        presetToggles.forEach { toggle ->
            toggle.isSelected = toggle.preset.mods == enabledMods
        }
    }

    override fun onSearchTermUpdate(searchTerm: String) {
        presetToggles.forEach { toggle ->
            toggle.isVisible = toggle.preset.name.searchContiguously(searchTerm)
        }
    }

    inner class ModPresetToggle(val preset: ModPreset) : LegacyButton() {

        private val nameText = UIText().apply {
            text = preset.name
        }

        private val modsIndicator = ModsIndicator().apply {
            iconSize = 21f
            mods = preset.mods.values
        }

        private val content = UILinearContainer().apply {
            orientation = UILinearContainer.Orientation.Vertical
            width = fill()

            +nameText
            +modsIndicator
        }

        init {
            width = fill()

            onActionUp = {
                if (isSelected) {
                    ModMenu.clear()
                } else {
                    ModMenu.modToggles.forEach { toggle ->
                        val presetMod = preset.mods[toggle.mod::class]

                        if (presetMod != null) {
                            // Prevent operations from modifying mods in the preset.
                            addMod(presetMod.deepCopy())
                        } else {
                            removeMod(toggle.mod)
                        }
                    }
                }
            }

            onActionLongPress = {
                UIMessageDialog().apply {
                    title = "Delete preset"
                    text = "Delete preset \"${preset.name}\"?"

                    addButton(UITextButton().apply {
                        text = "Delete"
                        isSelected = true
                        onActionUp = {
                            hide()
                            DatabaseManager.modPresetTable.delete(preset)
                            loadPresets()
                        }
                    })

                    addButton(UITextButton().apply {
                        text = "Cancel"
                        onActionUp = {
                            hide()
                        }
                    })
                    show()
                }
            }

            +content
        }

        override fun onApplyLegacySizes() {
            content.spacing = 8f.vu.px
            nameText.fontSize = LegacyUnits.fontSize("smallFont").vu.px
        }

        // ModIcon does not take the button colour, as inheritAncestorsColor = false did.
        override fun applyTint(tint: Color4) {
            nameText.color = tint
        }
    }
}
