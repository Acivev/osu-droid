package com.osudroid.ui.verktex

import com.osudroid.verktex.bridge.VerktexBridge
import com.reco1l.verktex.Platform
import com.reco1l.verktex.Verktex
import com.reco1l.verktex.graphics.GraphicsTexture
import com.reco1l.verktex.texture.Textures
import java.io.File
import ru.nsu.ccfit.zuev.osu.Config

/**
 * Skin-aware texture lookup for Verktex scenes. The skin directory wins over the bundled asset,
 * mirroring how legacy `TextureStore` and `ResourceManager` resolve textures.
 */
object SkinTextures {

    private const val KEY_PREFIX = "skin:"
    private const val ASSET_KEY_PREFIX = "asset:"

    /** Keys whose last load failed; not retried until [invalidate] or a [loadFile] with that key. */
    private val misses = mutableSetOf<String>()

    data class Resolved(val path: String, val isInternal: Boolean)

    /** Pure. A skin path with or without trailing slash is accepted. Skin dir first: <skinPath><name>.png, <name>.jpg, <name>@2x.png; else the asset path. */
    fun resolve(name: String, assetPath: String, skinPath: String, exists: (String) -> Boolean): Resolved {
        val dir = skinPath.trimEnd('/') + "/"
        val candidates = listOf("$dir$name.png", "$dir$name.jpg", "$dir$name@2x.png")
        val found = candidates.firstOrNull(exists)
        return if (found != null) Resolved(found, isInternal = false) else Resolved(assetPath, isInternal = true)
    }

    /**
     * Cached. Key "skin:<name>". Returns null when neither skin file nor asset decodes; that miss is
     * remembered and not retried until [invalidate]. Must be called on the update thread
     * (`com.osudroid.utils.updateThread { }` from elsewhere).
     */
    @JvmStatic
    @JvmOverloads
    fun get(name: String, assetPath: String = "gfx/$name.png"): GraphicsTexture.Region? {
        val key = KEY_PREFIX + name
        Textures[key]?.let { return it }
        if (key in misses) return null

        val resolved = resolve(name, assetPath, Config.getSkinPath()) { File(it).exists() }
        val region = Textures.load(key, resolved.path, isInternal = resolved.isInternal)
        if (region == null) misses += key
        return region
    }

    /**
     * Cached. Key "asset:<name>". The bundled asset only, for textures the legacy code loaded
     * internally, which a skin could not override. Misses are remembered like in [get]. Must be
     * called on the update thread.
     */
    @JvmStatic
    fun getAsset(name: String, assetPath: String): GraphicsTexture.Region? {
        val key = ASSET_KEY_PREFIX + name
        Textures[key]?.let { return it }
        if (key in misses) return null

        val region = Textures.load(key, assetPath, isInternal = true)
        if (region == null) misses += key
        return region
    }

    /**
     * Decodes an external file into a texture source. Safe from any thread; this is the expensive
     * half of [loadFile]. Returns null when the file is missing or cannot be decoded.
     */
    @JvmStatic
    fun decode(file: File): GraphicsTexture.Source? =
        try {
            Platform.provider.createTextureSource(file.path, isExternal = true)
        } catch (_: Exception) {
            null
        }

    /**
     * Registers a decoded [source] under [key], replacing an existing key and clearing any recorded
     * miss for it. Must be called on the update thread.
     */
    @JvmStatic
    @JvmOverloads
    fun register(key: String, source: GraphicsTexture.Source, standalone: Boolean = true): GraphicsTexture.Region? {
        VerktexBridge.checkUpdateThread()
        misses -= key
        return Textures.load(key, source, standalone)
    }

    /**
     * Synchronous [decode] followed by [register] for an arbitrary external file (backgrounds,
     * banners, avatars). Must be called on the update thread; prefer decoding off-thread.
     */
    @JvmStatic
    fun loadFile(key: String, file: File): GraphicsTexture.Region? {
        val source = decode(file)
        if (source == null) {
            misses -= key
            return null
        }
        return register(key, source, standalone = true)
    }

    /**
     * Unloads every "skin:" key and forgets recorded misses, called from UIEngine.onSkinChange. Safe
     * from any thread: the work is handed to the Verktex update worker.
     */
    @JvmStatic
    fun invalidate() {
        if (!VerktexBridge.isInitialized) return

        Verktex.scheduleOnUpdate {
            Textures.unloadAll { key, _ -> key.startsWith(KEY_PREFIX) }
            misses.clear()
            SharedBackground.invalidate()
        }
    }
}
