package com.osudroid.ui.v2.modmenu

import com.osudroid.mods.*
import com.osudroid.mods.settings.*
import com.osudroid.multiplayer.Multiplayer
import com.osudroid.ui.verktex.LegacyUnits
import com.osudroid.ui.verktex.LegacyUnits.vu
// The explicit import picks the Verktex twin over the same-package AndEngine ModIcon.
import com.osudroid.ui.verktex.ModIcon
import com.osudroid.ui.verktex.times
import com.osudroid.utils.updateThread
import com.reco1l.verktex.Verktex
import com.reco1l.verktex.data.Anchor
import com.reco1l.verktex.data.Axis
import com.reco1l.verktex.data.ClipMode
import com.reco1l.verktex.data.Color4
import com.reco1l.verktex.data.Padding
import com.reco1l.verktex.data.Vec2
import com.reco1l.verktex.data.fill
import com.reco1l.verktex.data.px
import com.reco1l.verktex.math.Easing
import com.reco1l.verktex.input.InputEvent
import com.reco1l.verktex.input.PointerEvent
import com.reco1l.verktex.math.FloatAnimation
import com.reco1l.verktex.time.TickingClock
import com.reco1l.verktex.ui.UIModal
import com.reco1l.verktex.ui.container.UIContainer
import com.reco1l.verktex.ui.container.UILinearContainer
import com.reco1l.verktex.ui.container.UIScrollableContainer
import com.reco1l.verktex.ui.text.UIText
import kotlin.reflect.KClass

class ModCustomizationMenu : UIModal(

    card = UIScrollableContainer().apply {
        scrollAxes = Axis.Y
        width = fill(0.475f)
        height = fill(0.75f)
        scaleOrigin = Anchor.TopCenter
        clipping = ClipMode.Bounds

        // The AndEngine UIModal coloured and rounded its card.
        style = Style { theme ->
            backgroundColor = theme.palette.accent * 0.15f
        }

        +UILinearContainer().apply {
            width = fill()
            orientation = UILinearContainer.Orientation.Vertical
        }
    }

) {

    private val modSettings = mutableMapOf<KClass<out Mod>, ModSettingsSection>()
    private val modSettingsContainer = card[0] as UILinearContainer
    private val modSettingComponents = mutableListOf<IModSettingComponent<*>>()

    // The linear 0.2 s show and hide of the AndEngine UIModal, which replace the Verktex ones.
    private val alphaAnimation = FloatAnimation(0f, ANIMATION_DURATION, Easing.None)
    private val scaleAnimation = FloatAnimation(0.9f, ANIMATION_DURATION, Easing.None)
    private var animationElapsed = ANIMATION_DURATION

    // LegacyUnits.scale the sizes were last applied for.
    private var appliedScale = 0f


    init {
        backgroundColor = Color4.Black.copy(alpha = 0.3f)
    }


    //region Mods

    fun onModAdded(mod: Mod) {
        if (mod.settings.isEmpty()) {
            return
        }

        val section = ModSettingsSection(mod)
        val previousSection = modSettings[mod::class]

        modSettings[mod::class] = section

        // Mods may be added off the update thread, e.g. when joining a multiplayer room.
        updateThread {
            Verktex.withTreeLock {
                previousSection?.removeSelf()
                modSettingsContainer += section
            }
        }
    }

    fun onModRemoved(mod: Mod) {
        val section = modSettings.remove(mod::class) ?: return

        updateThread { Verktex.withTreeLock { section.removeSelf() } }
    }

    fun isEmpty() = modSettings.isEmpty()

    //endregion

    //region Component lifecycle

    fun updateComponents() {
        modSettingComponents.forEach { it.update() }
    }

    fun updateComponentEnabledStates() {
        val room = Multiplayer.room
        val isHost = Multiplayer.isRoomHost

        modSettingComponents.forEach {
            it.isEnabled = if (room != null) {
                it.mod.isValidForMultiplayer && (isHost || (room.gameplaySettings.isFreeMod && it.mod.isValidForMultiplayerAsFreeMod))
            } else {
                true
            }
        }
    }

    @Suppress("UNCHECKED_CAST")
    private fun ModSettingComponent(mod: Mod, setting: ModSetting<*>): ModSettingComponent<*, *> {

        val component = when (setting) {
            is FloatModSetting ->
                if (setting.useManualInput) FloatModSettingTextInput(mod, setting)
                else FloatModSettingSlider(mod, setting)

            is IntegerModSetting ->
                if (setting.useManualInput) IntegerModSettingTextInput(mod, setting)
                else IntegerModSettingSlider(mod, setting)

            is NullableFloatModSetting ->
                if (setting.useManualInput) NullableFloatModSettingTextInput(mod, setting)
                else NullableFloatModSettingSlider(mod, setting)

            is NullableIntegerModSetting ->
                if (setting.useManualInput) NullableIntegerModSettingTextInput(mod, setting)
                else NullableIntegerModSettingSlider(mod, setting)

            is BooleanModSetting -> ModSettingCheckbox(mod, setting)

            is EnumModSetting<*> -> ModSettingEnum(mod, setting as EnumModSetting<Enum<*>>)

            else -> throw IllegalArgumentException("Unsupported mod setting type: ${setting::class.java.simpleName}")
        }

        modSettingComponents.add(component)
        component.update()

        component.isEnabled =
            if (Multiplayer.isMultiplayer && Multiplayer.room != null) {
                mod.isValidForMultiplayer && (Multiplayer.isRoomHost ||
                    (Multiplayer.room!!.gameplaySettings.isFreeMod && mod.isValidForMultiplayerAsFreeMod))
            } else true

        return component
    }

    //endregion

    //region Visibility

    override fun onShow() {
        super.onShow()
        alphaAnimation.target = 1f
        scaleAnimation.target = 1f
        animationElapsed = 0f
    }

    override fun onHide() {
        super.onHide()
        alphaAnimation.target = 0f
        scaleAnimation.target = 0.9f
        animationElapsed = 0f
    }

    override fun onShowAnimation(clock: TickingClock) = advanceAnimation(clock)

    override fun onHideAnimation(clock: TickingClock) = advanceAnimation(clock)

    // Both animations run for ANIMATION_DURATION from the same start.
    private fun advanceAnimation(clock: TickingClock): Boolean {
        animationElapsed += clock.elapsedTickTime

        alpha = alphaAnimation.update(clock)
        card.scale = scaleAnimation.update(clock)

        return animationElapsed >= ANIMATION_DURATION
    }

    //endregion

    //region Input

    // The AndEngine UIModal hid on the first touch outside the card and kept taking touches until hidden.
    override fun onInputEvent(event: InputEvent): Boolean {
        if (!isShown) {
            // Cancel events are dispatched to every component, so they must not be consumed.
            return isVisible && event is PointerEvent && event.action != PointerEvent.Action.Unknown
        }

        if (event is PointerEvent
            && event.action == PointerEvent.Action.Down
            && !staticBackdrop
            && !card.containsScenePoint(event.x, event.y)) {
            hide()
            return true
        }

        return super.onInputEvent(event)
    }

    //endregion

    override fun onUpdate(clock: TickingClock) {
        if (appliedScale != LegacyUnits.scale) {
            appliedScale = LegacyUnits.scale

            val card = card as UIScrollableContainer

            card.x = 60f.vu.px
            card.y = 90f.vu.px
            card.radius = 16f.vu.px
            card.scrollPadding = Vec2(0f, 300f.vu)
        }

        super.onUpdate(clock)
    }


    private inner class ModSettingsSection(val mod: Mod) : UILinearContainer() {

        private val header = UIContainer().apply {
            width = fill()
            backgroundColor = Color4.Black.copy(alpha = 0.05f)
        }

        private val icon = ModIcon(mod).apply {
            anchor = Anchor.CenterLeft
            origin = Anchor.CenterLeft
        }

        // Verktex linear containers place children at the top of the row, so the icon and the name
        // are centred in a plain container and the name starts after the icon width plus the spacing.
        private val nameText = UIText().apply {
            anchor = Anchor.CenterLeft
            origin = Anchor.CenterLeft
            text = mod.name.uppercase()
            wrapText = false
            style = Style { theme ->
                color = theme.palette.accent.copy(alpha = 0.9f)
            }
        }

        // LegacyUnits.scale the sizes were last applied for.
        private var appliedScale = 0f

        init {
            orientation = Orientation.Vertical
            width = fill()

            header += icon
            header += nameText
            +header

            mod.settings.forEach { +ModSettingComponent(mod, it) }
        }

        override fun onUpdate(clock: TickingClock) {
            if (appliedScale != LegacyUnits.scale) {
                appliedScale = LegacyUnits.scale

                padding = Padding(0f.px, 0f.px, 0f.px, 16f.vu.px)
                header.padding = Padding(20f.vu.px, 14f.vu.px)
                header.radius = 12f.vu.px
                icon.width = 34f.vu.px
                icon.height = 34f.vu.px
                nameText.x = (34f + 12f).vu.px
                nameText.fontSize = LegacyUnits.fontSize("smallFont").vu.px
            }

            super.onUpdate(clock)
        }
    }


    companion object {
        private const val ANIMATION_DURATION = 0.2f
    }
}
