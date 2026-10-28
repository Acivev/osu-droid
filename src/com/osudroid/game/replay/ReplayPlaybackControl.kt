package com.osudroid.game.replay

import com.reco1l.verktex.data.fill
import com.reco1l.verktex.ui.UICard

/**
 * Controls playback of gameplay when replaying.
 */
class ReplayPlaybackControl : UICard() {
    /**
     * Controls for playback rate.
     */
    val rateControl = ReplayPlaybackRate()

    /**
     * Controls for seeking.
     */
    val seekControl = ReplayPlaybackSeek()

    init {
        width = fill()
        title = "Playback"

        content {
            +seekControl
            +rateControl
        }
    }
}
