package com.osudroid.ui.verktex

import com.reco1l.verktex.graphics.GraphicsTexture
import com.reco1l.verktex.texture.Textures
import java.io.File
import ru.nsu.ccfit.zuev.osu.ResourceManager

/**
 * The Verktex mirror of the AndEngine background slot: the file last passed to
 * `ResourceManager.loadBackground`, decoded once and shared by every Verktex screen that shows it.
 */
object SharedBackground {

    const val KEY = "::background#verktex"

    /** The background file to mirror. Replaceable for tests. */
    internal var fileProvider: () -> String? = { ResourceManager.getInstance().backgroundFile }

    /** The file [KEY] holds, null when nothing is loaded. */
    private var loadedFile: String? = null

    /**
     * Update thread only. Returns the region of the current background file, decoding it only when
     * the file changed since the last call; the previous region is released. Null when there is no
     * file or it cannot be decoded.
     */
    @JvmStatic
    fun region(): GraphicsTexture.Region? {
        val file = fileProvider()

        if (file != null && file == loadedFile) {
            Textures[KEY]?.let { return it }
        }

        Textures.unload(KEY)
        loadedFile = null

        if (file == null) {
            return null
        }

        val source = SkinTextures.decode(File(file)) ?: return null
        val region = SkinTextures.register(KEY, source, true)
        if (region != null) {
            loadedFile = file
        }
        return region
    }

    /** Forgets the loaded file so the next [region] decodes it again. */
    @JvmStatic
    fun invalidate() {
        loadedFile = null
    }
}
