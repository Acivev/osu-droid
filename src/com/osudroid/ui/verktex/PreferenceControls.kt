package com.osudroid.ui.verktex

import com.reco1l.verktex.ui.form.FormCheckbox
import com.reco1l.verktex.ui.form.FormSlider
import ru.nsu.ccfit.zuev.osu.Config

/**
 * The AndEngine IntPreferenceSlider: a [FormSlider] starting at the preference value and writing
 * every change back to it.
 */
class IntPreferenceSlider(private val preferenceKey: String, fallbackValue: Int = 0) : FormSlider(Config.getInt(preferenceKey, fallbackValue).toFloat()) {
    override fun onControlValueChanged() {
        Config.setInt(preferenceKey, control.value.toInt())
        super.onControlValueChanged()
    }
}

/**
 * The AndEngine PreferenceCheckbox: a [FormCheckbox] starting at the preference value and writing
 * every change back to it.
 */
class PreferenceCheckbox(private val preferenceKey: String, fallback: Boolean = false) : FormCheckbox(Config.getBoolean(preferenceKey, fallback)) {
    override fun onControlValueChanged() {
        Config.setBoolean(preferenceKey, value)
        super.onControlValueChanged()
    }
}
