package com.osudroid.ui.verktex

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import com.osudroid.verktex.bridge.VerktexBridge
import com.reco1l.verktex.Platform
import com.reco1l.verktex.Verktex
import com.reco1l.verktex.android.AndroidTextureSource
import com.reco1l.verktex.graphics.GraphicsTexture
import com.reco1l.verktex.texture.Textures
import com.osudroid.ui.skinning.StringSkinData
import java.io.File
import ru.nsu.ccfit.zuev.osu.Config
import ru.nsu.ccfit.zuev.osu.ResourceManager
import ru.nsu.ccfit.zuev.skins.BeatmapSkinManager

/**
 * Skin-aware texture lookup for Verktex scenes. The skin directory wins over the bundled asset,
 * mirroring how legacy `TextureStore` and `ResourceManager` resolve textures.
 */
object SkinTextures {

    private const val KEY_PREFIX = "skin:"
    private const val ASSET_KEY_PREFIX = "asset:"
    private const val BEATMAP_KEY_PREFIX = "beatmap:"

    /** Keys whose last load failed; not retried until [invalidate] or a [loadFile] with that key. */
    private val misses = mutableSetOf<String>()

    /**
     * A resolved texture file. [isHD] is true when the `@2x` candidate matched, such a file is decoded
     * at half size as AndEngine's `TextureStore` did.
     */
    data class Resolved(val path: String, val isInternal: Boolean, val isHD: Boolean = false)

    /** Pure. A skin path with or without trailing slash is accepted. Skin dir first: <skinPath><name>.png, <name>.jpg, <name>@2x.png; else the asset path. */
    fun resolve(name: String, assetPath: String, skinPath: String, exists: (String) -> Boolean): Resolved {
        val dir = skinPath.trimEnd('/') + "/"
        val hdCandidate = "$dir$name@2x.png"
        val candidates = listOf("$dir$name.png", "$dir$name.jpg", hdCandidate)
        val found = candidates.firstOrNull(exists)
        return if (found != null) Resolved(found, isInternal = false, isHD = found == hdCandidate) else Resolved(assetPath, isInternal = true)
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
        val region = if (resolved.isHD) loadHD(key, File(resolved.path)) else Textures.load(key, resolved.path, isInternal = resolved.isInternal)
        if (region == null) misses += key
        return region
    }

    /**
     * Loads an `@2x` skin file at half size under [key], null when it does not decode.
     */
    private fun loadHD(key: String, file: File): GraphicsTexture.Region? {
        val source = decode(file, inSampleSize = 2) ?: return null
        return Textures.load(key, source)
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

    //region Beatmap skin

    /**
     * Pure. The beatmap skin file for [name] while the beatmap skin is [enabled], else null. [customFile]
     * gives the file `ResourceManager.loadCustomTexture` loaded for a texture name of the current
     * beatmap (same names, animated-frame naming and slider-circle fallbacks).
     */
    fun resolveBeatmapSkin(name: String, enabled: Boolean, customFile: (String) -> File?): File? =
        if (enabled) customFile(name) else null

    /**
     * The gameplay lookup of `ResourceManager.getTexture`: while the beatmap skin is enabled, the
     * current beatmap's file for [name] wins, else the user skin and the asset ([get]). Beatmap
     * textures are cached under "beatmap:<file path>", so one beatmap's texture never stands in for
     * another beatmap's or the user skin's. Must be called on the update thread.
     */
    @JvmStatic
    @JvmOverloads
    fun getWithBeatmapSkin(name: String, assetPath: String = "gfx/$name.png"): GraphicsTexture.Region? =
        getWithBeatmapSkin(name, assetPath, BeatmapSkinManager.isSkinEnabled(), ResourceManager.getInstance()::getCustomTextureFile)

    /**
     * [getWithBeatmapSkin] with the beatmap skin state given, the seam the tests use.
     */
    fun getWithBeatmapSkin(name: String, assetPath: String, enabled: Boolean, customFile: (String) -> File?): GraphicsTexture.Region? {
        val file = resolveBeatmapSkin(name, enabled, customFile)

        if (file != null) {
            val key = BEATMAP_KEY_PREFIX + file.path
            Textures[key]?.let { return it }

            if (key !in misses) {
                val region = if (file.name.contains("@2x.")) loadHD(key, file) else Textures.load(key, file.path, isInternal = false)
                if (region != null) return region
                misses += key
            }
        }

        return get(name, assetPath)
    }

    /**
     * The gameplay lookup of `ResourceManager.getTextureWithPrefix`: the beatmap skin's texture under the
     * default prefix while enabled, else the user skin's current prefix, else the default prefix. Must be
     * called on the update thread.
     */
    @JvmStatic
    fun getWithPrefix(prefix: StringSkinData, name: String): GraphicsTexture.Region? {
        val defaultName = prefix.defaultValue + "-" + name

        resolveBeatmapSkin(defaultName, BeatmapSkinManager.isSkinEnabled(), ResourceManager.getInstance()::getCustomTextureFile)?.let {
            return getWithBeatmapSkin(defaultName)
        }

        return get((prefix.currentValue + "-" + name).replace("\\", ""))
            ?: if (prefix.isDefault) null else get(defaultName)
    }

    /**
     * Unloads every beatmap skin texture and forgets their misses, called where `ResourceManager` clears
     * its custom (beatmap) store. Safe from any thread: the work is handed to the Verktex update worker.
     */
    @JvmStatic
    fun releaseBeatmapSkin() {
        if (!VerktexBridge.isInitialized) return

        Verktex.scheduleOnUpdate {
            Textures.unloadAll { key, _ -> key.startsWith(BEATMAP_KEY_PREFIX) }
            misses.removeAll { it.startsWith(BEATMAP_KEY_PREFIX) }
        }
    }

    //endregion

    /**
     * Decodes an external file into a texture source. Safe from any thread; this is the expensive
     * half of [loadFile]. Returns null when the file is missing or cannot be decoded.
     *
     * @param inSampleSize `BitmapFactory.Options.inSampleSize`: 2 decodes an `@2x` file at half size
     * (as AndEngine's `QualityFileBitmapSource` did), 1 decodes it as it is through the platform provider.
     */
    @JvmStatic
    @JvmOverloads
    fun decode(file: File, inSampleSize: Int = 1): GraphicsTexture.Source? =
        try {
            if (inSampleSize == 1) {
                Platform.provider.createTextureSource(file.path, isExternal = true)
            } else {
                val options = BitmapFactory.Options().apply {
                    inPreferredConfig = Bitmap.Config.ARGB_8888
                    this.inSampleSize = inSampleSize
                }
                BitmapFactory.decodeFile(file.path, options)?.let { AndroidTextureSource(it) }
            }
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
     * Unloads every "skin:" key and forgets recorded misses, called from [onSkinChange]. Safe
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

    /**
     * Called when the skin is changed.
     */
    @JvmStatic
    fun onSkinChange() {
        invalidate()

        // Runs after the invalidation, both are tasks of the Verktex update worker.
        if (VerktexBridge.isInitialized) {
            Verktex.scheduleOnUpdate {
                Verktex.scene.propagate { (it as? ISkinnable)?.onSkinChanged() }
            }
        }
    }
}
