package com.osudroid.ui.v2.modmenu

import com.osudroid.mods.Mod
import com.osudroid.mods.settings.*
import com.reco1l.verktex.ui.control.UIControl
import com.reco1l.verktex.ui.control.UISelect
import com.reco1l.verktex.ui.form.FormControl
import com.reco1l.verktex.ui.form.FormSelect

class ModSettingEnum(mod: Mod, setting: EnumModSetting<Enum<*>>) :
    ModSettingComponent<Enum<*>, List<Enum<*>>>(mod, setting),
    IModSettingComponent<Enum<*>> {

    // The AndEngine FormSelect started its select empty, so the first update() set the value and
    // showed the selected option. The Verktex one starts with the initial value, which would leave
    // the placeholder shown while the setting still has that value.
    @Suppress("UNCHECKED_CAST")
    override fun createControl() = FormSelect(listOf(setting.initialValue)).apply {
        this.control.value = emptyList()
    } as FormControl<List<Enum<*>>, UIControl<List<Enum<*>>>>

    @Suppress("UNCHECKED_CAST")
    override fun update() {
        setting as EnumModSetting<*>

        (control.control as UISelect<Enum<*>>).apply {
            options = setting.entries.map {
                UISelect.Option(
                    value = it,
                    text = setting.valueFormatter?.invoke(setting, it) ?: it.toString()
                )
            }
        }

        super.update()
    }

    // We return the first value because the mod setting only allows single selection.
    override fun convertControlValue(value: List<Enum<*>>) = value[0]
    override fun convertSettingValue(value: Enum<*>) = listOf(value)

}
