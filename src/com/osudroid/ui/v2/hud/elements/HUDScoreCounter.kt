package com.osudroid.ui.v2.hud.elements

import com.osudroid.ui.v2.hud.HUDElement
import com.osudroid.ui.v2.SpriteFont
import com.osudroid.ui.verktex.LegacyUnits.vu
import com.reco1l.verktex.time.TickingClock
import com.rian.framework.RollingLongCounter
import ru.nsu.ccfit.zuev.osu.game.GameScene
import ru.nsu.ccfit.zuev.skins.OsuSkin
import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.util.Locale

class HUDScoreCounter : HUDElement() {

    private val sprite = SpriteFont(OsuSkin.get().scorePrefix)
    private val format = DecimalFormat("00000000", DecimalFormatSymbols(Locale.US))

    private var value = 0L
        set(value) {
            if (field != value) {
                field = value
                sprite.text = format.format(value)
            }
        }

    private val counter = RollingLongCounter(0L).apply { rollingDuration = 1f }

    init {
        sprite.spacing = -OsuSkin.get().scoreOverlap.vu

        val digitRange = '0'..'9'
        val maxDigitWidth = digitRange.maxOfOrNull { sprite.characters[it]?.width?.toFloat() ?: 0f } ?: 0f

        sprite.fixedCharWidths = digitRange.associateWith { maxDigitWidth }
        sprite.text = format.format(0)

        this += sprite
    }

    override fun onGameplayUpdate(game: GameScene, secondsElapsed: Float) {
        counter.targetValue = game.stat.totalScoreWithMultiplier
    }

    override fun onUpdate(clock: TickingClock) {
        value = counter.currentValue

        // The AndEngine update handler ran before the children.
        counter.onUpdate(clock.elapsedTickTime)
        super.onUpdate(clock)
    }
}