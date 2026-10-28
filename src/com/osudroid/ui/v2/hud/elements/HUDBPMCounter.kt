package com.osudroid.ui.v2.hud.elements

import com.reco1l.verktex.time.TickingClock
import com.rian.framework.RollingIntCounter
import kotlin.math.roundToInt
import ru.nsu.ccfit.zuev.osu.game.GameHelper
import ru.nsu.ccfit.zuev.osu.game.GameScene

class HUDBPMCounter : HUDStatisticCounter("BPM") {

    override val name = "BPM counter"

    private var value = 0
        set(value) {
            if (field != value) {
                field = value
                valueText.text = value.toString()
            }
        }

    private val counter = RollingIntCounter(0).apply { rollingDuration = 0.375f }

    override fun onGameplayUpdate(gameScene: GameScene, secondsElapsed: Float) {
        val beatmap = gameScene.playableBeatmap ?: return
        val replaySettingsPanel = gameScene.replaySettingsPanel

        val timingPoint = beatmap.controlPoints.timing.controlPointAt(gameScene.elapsedTime * 1000.0)

        // GameHelper.getSpeedMultiplier accounts for rate set by ReplaySettingsPanel, so it is not a representative of
        // the actual playback rate. We need to divide by the replay playback rate to get the correct BPM.
        val replayPlaybackRate = (replaySettingsPanel?.playbackControl?.rateControl?.rate ?: 1f)
        val bpm = timingPoint.bpm * GameHelper.getSpeedMultiplier() / replayPlaybackRate

        counter.targetValue = bpm.roundToInt()
    }

    override fun onUpdate(clock: TickingClock) {
        value = counter.currentValue

        // The AndEngine update handler ran before the children.
        counter.onUpdate(clock.elapsedTickTime)
        super.onUpdate(clock)
    }
}