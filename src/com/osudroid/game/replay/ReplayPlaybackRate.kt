package com.osudroid.game.replay

import com.osudroid.ui.verktex.LegacyUnits.vu
import com.reco1l.verktex.data.Anchor
import com.reco1l.verktex.data.Padding
import com.reco1l.verktex.data.fill
import com.reco1l.verktex.data.px
import com.reco1l.verktex.ui.container.UILinearContainer
import com.reco1l.verktex.ui.container.linearContainer
import com.reco1l.verktex.ui.form.FormSlider
import com.reco1l.verktex.ui.textButton
import java.text.DecimalFormat

class ReplayPlaybackRate : UILinearContainer() {
    /**
     * The rate at which gameplay should progress.
     */
    var rate = 1f
        private set

    private val rateFormatter = DecimalFormat("0.00x")

    init {
        orientation = Orientation.Vertical
        width = fill()

        val slider = FormSlider(rate).apply {
            label = "Playback speed"
            control.min = 0.05f
            control.max = 2f
            valueFormatter = { rateFormatter.format(it) }
            onValueChanged = { rate = it }
        }

        +slider

        linearContainer {
            spacing = 10f.vu.px
            padding = Padding(0f.px, 16f.vu.px)
            anchor = Anchor.TopCenter
            origin = Anchor.TopCenter

            fun addStepButton(step: Float) = textButton {
                text = "%+.2f".format(step)
                height = 42f.vu.px
                padding = Padding(12f.vu.px, 0f.px)
                onActionUp = { slider.value += step }
            }

            addStepButton(-0.05f)
            addStepButton(-0.01f)
            addStepButton(0.01f)
            addStepButton(0.05f)
        }
    }
}