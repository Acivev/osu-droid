package com.osudroid.resources

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import ru.nsu.ccfit.zuev.osu.Config
import ru.nsu.ccfit.zuev.osu.helper.Debug
import java.io.File
import java.io.FileInputStream
import java.io.IOException
import java.io.InputStream
import java.util.concurrent.ConcurrentHashMap

/**
 * A texture the catalog knows to be loadable: where it comes from and the size it decodes to.
 * Nothing is uploaded; the pixels are drawn through Verktex (`SkinTextures`).
 */
class TextureEntry(val source: Source, val file: File?, val width: Int, val height: Int) {

    enum class Source { SkinFile, DefaultAsset, BeatmapSkin, HighQualityAsset, HighQualityFile }
}

/**
 * A named cache of loaded textures plus their animation frame counts, safe to read and write from
 * any thread. Historically (in `ResourceManager`) this was a plain `HashMap` written from
 * background skin-loading threads (see `BeatmapSkinManager`) while the update/GL thread read it
 * concurrently during rendering - corrupting under that access pattern. Backed by
 * [ConcurrentHashMap] here instead, so every operation below is safe regardless of calling thread.
 *
 * One [TextureStore] instance is one flat cache; the default-skin/user-skin/beatmap-skin layering
 * ([BeatmapSkinManager]'s override behavior) is composed by the caller out of two instances, not
 * built into this class.
 */
class TextureStore {

    // Set via init(), mirroring ResourceManager.Init() - not constructor parameters, so that
    // bookkeeping-only tests can construct a store without needing a real Context (the field is
    // only touched by the asset loads below).
    private lateinit var context: Context

    private val textures = ConcurrentHashMap<String, TextureEntry>()
    private val frameCounts = ConcurrentHashMap<String, Int>()

    fun init(context: Context) {
        this.context = context
    }

    /**
     * Sentinel standing in for a `null` cache entry - e.g. the "lighting" texture, which
     * `ResourceManager.loadSkin` intentionally marks as "checked, absent" to suppress repeated
     * lazy-load attempts. [ConcurrentHashMap] forbids real `null` values, so this is substituted
     * internally and translated back to `null` by [get]/[remove], preserving the exact external
     * contract callers (e.g. `GameScene`'s `getTexture("lighting") != null` check) rely on.
     */
    private val ABSENT = TextureEntry(TextureEntry.Source.DefaultAsset, null, 0, 0)

    //region Bookkeeping

    /** Returns the cached entry for [name], or `null` if absent or explicitly marked absent via [markAbsent]. */
    fun get(name: String): TextureEntry? = textures[name]?.takeIf { it !== ABSENT }

    fun containsKey(name: String): Boolean = textures.containsKey(name)

    /**
     * Unconditionally sets [name] to [entry] - a `null` entry is stored as [markAbsent] would,
     * matching the original `Map<String, TextureRegion>.put(name, someNullableGet())` pass-through
     * pattern `ResourceManager.loadBackground` relies on (its "::background" fallback stores
     * whatever `get("menu-background")` returned, including `null` if that itself was never loaded).
     */
    fun put(name: String, entry: TextureEntry?) {
        textures[name] = entry ?: ABSENT
    }

    /**
     * Marks [name] as checked-and-absent: [containsKey] becomes true, [get] keeps returning `null`.
     * Unlike [put], does not override an already-present entry (matching the `if
     * (!textures.containsKey("lighting")) textures.put("lighting", null)` guard every original call
     * site used this for).
     */
    fun markAbsent(name: String) {
        textures.putIfAbsent(name, ABSENT)
    }

    fun remove(name: String): TextureEntry? = textures.remove(name)?.takeIf { it !== ABSENT }

    val keys: Set<String> get() = textures.keys

    fun getFrameCount(name: String): Int = frameCounts[name] ?: -1

    /** Mirrors `ResourceManager.parseFrameIndex`'s "only grow, never shrink" update rule. */
    fun growFrameCount(name: String, atLeast: Int) {
        frameCounts.merge(name, atLeast) { existing, candidate -> maxOf(existing, candidate) }
    }

    fun removeFrameCount(name: String) {
        frameCounts.remove(name)
    }

    fun clearFrameCounts() = frameCounts.clear()

    fun clear() {
        textures.clear()
        frameCounts.clear()
    }

    //endregion

    //region Loads

    /**
     * Loads a texture either from an external file (with `@2x` HD-variant fallback when the exact
     * path doesn't exist, decoded at half size) or from the app's bundled assets, registers it
     * under [resname], and returns it. Returns `null`, storing nothing, when the file (and its
     * `@2x` variant) doesn't exist or doesn't decode.
     */
    fun load(resname: String, file: String, external: Boolean): TextureEntry? {
        val entry: TextureEntry

        if (external) {
            var texFile = File(file)
            var isHDTexture = false

            if (!texFile.exists()) {
                val dotIndex = file.lastIndexOf('.')
                texFile = File(file.substring(0, dotIndex) + "@2x" + file.substring(dotIndex))
                isHDTexture = texFile.exists()

                if (!isHDTexture) {
                    return null
                }
            }

            entry = decodeFile(texFile, if (isHDTexture) 2 else 1, TextureEntry.Source.SkinFile) ?: return null
        } else {
            entry = decodeAsset(file, TextureEntry.Source.DefaultAsset) ?: return null
        }

        textures[resname] = entry
        return entry
    }

    fun loadHighQualityAsset(resname: String, file: String): TextureEntry? {
        val entry = decodeAsset(file, TextureEntry.Source.HighQualityAsset) ?: return null

        textures[resname] = entry
        return entry
    }

    fun loadHighQualityFile(resname: String, file: File): TextureEntry? {
        val entry = decodeFile(file, 1, TextureEntry.Source.HighQualityFile) ?: return null

        textures[resname] = entry
        return entry
    }

    fun unload(name: String) {
        if (get(name) == null) return
        textures.remove(name)
    }

    // Assets decode with the texture quality as their sample size, as QualityAssetBitmapSource did.
    private fun decodeAsset(file: String, source: TextureEntry.Source): TextureEntry? =
        decode({ context.assets.open(file) }, Config.getTextureQuality())?.let { (width, height) ->
            TextureEntry(source, null, width, height)
        }

    //endregion

    companion object {

        /**
         * Decodes [file] with [sampleSize] (1, or 2 for an `@2x` file, as QualityFileBitmapSource
         * did) and returns its entry, or `null` when it doesn't decode. Stores nothing.
         */
        @JvmStatic
        fun decodeFile(file: File, sampleSize: Int, source: TextureEntry.Source): TextureEntry? =
            decode({ FileInputStream(file) }, sampleSize, file)?.let { (width, height) ->
                TextureEntry(source, file, width, height)
            }

        /**
         * Bounds, then a full decode that is discarded; the bounds' size, or `null` on failure. A
         * failed [file] read is logged as QualityFileBitmapSource logged it; assets fail silently.
         */
        private fun decode(open: () -> InputStream, sampleSize: Int, file: File? = null): Pair<Int, Int>? {
            val bounds = BitmapFactory.Options().also {
                it.inJustDecodeBounds = true
                it.inSampleSize = sampleSize
            }

            try {
                open().use { BitmapFactory.decodeStream(it, null, bounds) }
            } catch (e: IOException) {
                if (file != null) {
                    Debug.e("Failed loading Bitmap in FileBitmapTextureAtlasSource. File: $file", e)
                }
                return null
            }

            if (bounds.outWidth <= 0 || bounds.outHeight <= 0) {
                return null
            }

            val options = BitmapFactory.Options().also {
                it.inPreferredConfig = Bitmap.Config.ARGB_8888
                it.inSampleSize = sampleSize
            }

            val bitmap = try {
                open().use { BitmapFactory.decodeStream(it, null, options) }
            } catch (e: IOException) {
                if (file != null) {
                    Debug.e("Failed loading Bitmap in FileBitmapTextureAtlasSource. File: $file", e)
                }
                null
            } ?: return null

            bitmap.recycle()
            return bounds.outWidth to bounds.outHeight
        }
    }
}
