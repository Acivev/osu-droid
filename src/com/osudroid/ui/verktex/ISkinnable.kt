package com.osudroid.ui.verktex

/**
 * A Verktex component holding [SkinTextures] regions. Notified on the update thread after a skin
 * change unloaded them, so it can look them up again.
 */
interface ISkinnable {
    fun onSkinChanged()
}
