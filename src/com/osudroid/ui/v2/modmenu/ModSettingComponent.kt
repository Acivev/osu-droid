package com.osudroid.ui.v2.modmenu

import com.osudroid.mods.Mod
import com.osudroid.mods.settings.ModSetting
import com.reco1l.verktex.data.fill
import com.reco1l.verktex.math.Easing
import com.reco1l.verktex.math.FloatAnimation
import com.reco1l.verktex.time.TickingClock
import com.reco1l.verktex.ui.container.UIContainer
import com.reco1l.verktex.ui.control.UIControl
import com.reco1l.verktex.ui.form.FormControl

/**
 * Interface for a component that controls a [ModSetting].
 */
interface IModSettingComponent<V> {
    /**
     * The [Mod] that this [IModSettingComponent] belongs to.
     */
    val mod: Mod

    /**
     * The [ModSetting] that is controlled by this [IModSettingComponent].
     */
    val setting: ModSetting<V>

    /**
     * Whether this [IModSettingComponent] is enabled.
     *
     * If `false`, the user won't be able to interact with this [IModSettingComponent].
     */
    var isEnabled: Boolean

    /**
     * Updates this [IModSettingComponent] to reflect the current state of the [ModSetting].
     */
    fun update()
}

/**
 * A component that controls a [ModSetting].
 *
 * @param TSettingValue The type of the value in the [ModSetting].
 * @param TControlValue The type of the value in the [FormControl] that is used to display this [ModSettingComponent].
 */
sealed class ModSettingComponent<TSettingValue, TControlValue : Any>(
    final override val mod: Mod,
    final override val setting: ModSetting<TSettingValue>
) : UIContainer(), IModSettingComponent<TSettingValue> {
    /**
     * The [FormControl] that is used to display this [ModSettingComponent].
     */
    protected val control = createControl().apply {
        width = fill()

        label = setting.name

        valueFormatter = {
            setting.valueFormatter?.invoke(setting, convertControlValue(it)) ?: it.toString()
        }

        onValueChanged = {
            setting.value = convertControlValue(it)
            ModMenu.queueModChange(mod)
        }
    }

    final override var isEnabled by control::isEnabled

    // The linear fade of the AndEngine FormControl, which replaces the Verktex one.
    private val enabledAlpha = FloatAnimation(1f, 0.2f, Easing.None)

    init {
        width = fill()
        +control
    }

    override fun update() {
        control.apply {
            defaultValue = convertSettingValue(setting.defaultValue)
            value = convertSettingValue(setting.value)
        }
    }

    override fun onUpdate(clock: TickingClock) {
        super.onUpdate(clock)

        enabledAlpha.target = if (isEnabled) 1f else 0.25f
        control.alpha = enabledAlpha.update(clock)
    }

    /**
     * Creates the [FormControl] that is used to display this [ModSettingComponent].
     */
    protected abstract fun createControl(): FormControl<TControlValue, UIControl<TControlValue>>

    /**
     * Converts a value from the [ModSetting] to a value that can be displayed in the [FormControl].
     *
     * @param value The value from the [ModSetting].
     * @return The value that can be displayed in the [FormControl].
     */
    protected abstract fun convertSettingValue(value: TSettingValue): TControlValue

    /**
     * Converts a value from the [FormControl] to a value that can be used in the [ModSetting].
     *
     * @param value The value from the [FormControl].
     * @return The value that can be used in the [ModSetting].
     */
    protected abstract fun convertControlValue(value: TControlValue): TSettingValue
}
