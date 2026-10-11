package com.osudroid.verktex

import com.reco1l.verktex.data.Axis
import com.reco1l.verktex.data.Color4
import com.reco1l.verktex.data.Padding
import com.reco1l.verktex.data.dip
import com.reco1l.verktex.data.fill
import com.reco1l.verktex.ui.UIScene
import com.reco1l.verktex.ui.container.UILinearContainer
import com.reco1l.verktex.ui.container.linearContainer
import com.reco1l.verktex.ui.container.scrollableContainer
import com.reco1l.verktex.ui.shape.box
import com.reco1l.verktex.ui.text.text
import com.reco1l.verktex.ui.textButton

class VerktexTestScene : UIScene() {

    private var clicks = 0

    init {
        backgroundColor = Color4(0xFF1E1E2E)

        scrollableContainer {
            width = fill()
            height = fill()
            scrollAxes = Axis.Y

            linearContainer {
                orientation = UILinearContainer.Orientation.Vertical
                width = fill()
                spacing = 12f.dip
                padding = Padding(24f.dip)

                text {
                    text = "Verktex test scene"
                    fontSize = 28f.dip
                }

                text {
                    text = "If you can read this, text rendering works."
                }

                val counter = text {
                    text = "Button clicks: 0"
                }

                textButton {
                    text = "Click me"
                    onActionUp = {
                        clicks++
                        counter.text = "Button clicks: $clicks"
                    }
                }

                linearContainer {
                    orientation = UILinearContainer.Orientation.Horizontal
                    spacing = 8f.dip

                    for (color in listOf(0xFFF38BA8, 0xFFA6E3A1, 0xFF89B4FA, 0xFFF9E2AF)) {
                        box {
                            width = 48f.dip
                            height = 48f.dip
                            radius = 8f.dip
                            backgroundColor = Color4(color)
                        }
                    }
                }

                for (i in 1..30) {
                    text {
                        text = "Scroll row $i"
                    }
                }
            }
        }
    }
}
