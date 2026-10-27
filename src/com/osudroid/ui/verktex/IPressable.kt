package com.osudroid.ui.verktex

/**
 * A Verktex component that tracks a press. Told to drop it when its scene is shown again, the
 * release may have gone to AndEngine while the scene was not shown.
 */
interface IPressable {
    fun cancelPress()
}
