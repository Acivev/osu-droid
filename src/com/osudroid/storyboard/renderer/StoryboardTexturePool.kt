package com.osudroid.storyboard.renderer

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import com.osudroid.storyboard.model.Storyboard
import com.osudroid.ui.verktex.SkinTextures
import com.osudroid.verktex.bridge.VerktexBridge
import com.reco1l.verktex.Verktex
import com.reco1l.verktex.graphics.GraphicsTexture
import com.reco1l.verktex.texture.Textures
import java.io.File
import java.util.concurrent.atomic.AtomicInteger
import ru.nsu.ccfit.zuev.osu.ResourceManager

/**
 * Loads and manages the textures of a storyboard, rooted at the beatmap folder.
 *
 * Textures that are referenced frequently (at least [PACK_USAGE_THRESHOLD] times) and are small
 * enough are shelf-packed into shared page textures so that the sprite batch can draw them
 * without texture switches. All other textures are loaded individually. Missing or corrupt files
 * resolve to a 1x1 red placeholder, matching the previous implementation.
 *
 * Loading happens in two steps: [load] decodes and packs the bitmaps on the calling (worker)
 * thread, [register] turns them into Verktex textures on the update thread. Every path a draw can
 * ask for is registered up front, an unknown path at draw time yields the placeholder.
 */
class StoryboardTexturePool @JvmOverloads constructor(
    private val directory: File,

    /**
     * Whether textures of the current skin are preferred over the textures in [directory].
     */
    private val useSkinSprites: Boolean = false
) {
    private val textures = HashMap<String, GraphicsTexture.Region>()

    // What load() decoded for register(): the single textures, the pages and the names provided by the skin.
    private val singles = LinkedHashMap<String, StoryboardTextureSource>()
    private val pages = ArrayList<Page>()
    private val skinNames = ArrayList<String>()

    /**
     * The prefix of the [Textures] keys of this pool.
     */
    internal val keyPrefix = "$KEY_PREFIX${nextPoolId.incrementAndGet()}:"

    private var placeholder: GraphicsTexture.Region? = null

    /**
     * The white 1x1 region the black quads are drawn with in black, available after [register].
     */
    var whiteRegion: GraphicsTexture.Region? = null
        private set

    private val bitmapOptions = BitmapFactory.Options().also { it.inPremultiplied = true }

    // Shelf packing state.
    private var currentPage = 0
    private var currentX = 0
    private var currentY = 0
    private var lineMaxY = 0

    /**
     * Loads all textures used by a storyboard.
     *
     * @param storyboard The storyboard to load the textures of.
     */
    fun load(storyboard: Storyboard) {
        val counts = storyboard.textureUsageCounts().toMutableMap()

        val background = storyboard.backgroundFilename
        if (background != null && !storyboard.usesBackgroundImage()) {
            counts[background] = (counts[background] ?: 0) + 1
        }

        if (useSkinSprites) {
            // Textures provided by the skin are owned by the skin, so they are neither packed nor
            // loaded from the beatmap folder.
            // Their Verktex regions are fetched in register().
            counts.keys.removeAll { name ->
                if (!isSkinTexture(name)) return@removeAll false
                skinNames.add(name)
                true
            }
        }

        packAll(counts.keys.filter { counts[it]!! >= PACK_USAGE_THRESHOLD })

        for (name in counts.keys) {
            if (counts[name]!! < PACK_USAGE_THRESHOLD) {
                add(name)
            }
        }
    }

    /**
     * Creates the Verktex textures of what [load] decoded. Must be called on the update thread,
     * once, after [load].
     */
    fun register() {
        VerktexBridge.checkUpdateThread()

        val placeholder = shared(PLACEHOLDER_KEY, ::errorBitmap)
        this.placeholder = placeholder
        val whiteRegion = shared(WHITE_KEY, ::whiteBitmap)
        this.whiteRegion = whiteRegion

        val uploaded = LinkedHashSet<GraphicsTexture>()
        uploaded.add(placeholder.texture)
        uploaded.add(whiteRegion.texture)

        for ((index, page) in pages.withIndex()) {
            val region = Textures.load(keyPrefix + "page" + index, page.source, standalone = true)
            region?.let { uploaded.add(it.texture) }

            for (info in page.infos) {
                textures[info.name] = if (region == null) placeholder.also { page.source.recycle() } else GraphicsTexture.Region(
                    region.texture, page.source,
                    info.x.toFloat(), info.y.toFloat(),
                    info.width.toFloat(), info.height.toFloat()
                )
            }
        }

        for ((name, source) in singles) {
            val region = Textures.load(keyPrefix + name, source, standalone = true)
            region?.let { uploaded.add(it.texture) }
            textures[name] = region ?: placeholder.also { source.recycle() }
        }

        for (name in skinNames) {
            textures[name] = SkinTextures.getWithBeatmapSkin(name.substringBeforeLast('.')) ?: placeholder
        }

        // The textures own the sources now.
        pages.clear()
        singles.clear()
        skinNames.clear()

        // Verktex uploads a texture's regions when it is first bound, which would stall the frame that first
        // draws them. AndEngine uploaded them in the frames right after the load, so they are bound on the next
        // frame here. Every draw binds its own texture, so binding them ahead of time changes nothing that is drawn.
        Verktex.scheduleOnDraw {
            for (texture in uploaded) {
                texture.bind()
            }
        }
    }

    /**
     * Returns the texture region of the given path, or the placeholder if the path was not
     * registered. Nothing is loaded here.
     *
     * @param name The texture path relative to the beatmap folder.
     */
    fun get(name: String): GraphicsTexture.Region =
        textures[name] ?: checkNotNull(placeholder) { "The storyboard textures are not registered." }

    /**
     * Unloads all textures created by this pool. The unload runs on the update thread, it is
     * scheduled there when called from another thread.
     */
    fun clear() {
        textures.clear()
        placeholder = null
        whiteRegion = null

        // Never handed to a texture, so nothing else uploads or recycles them.
        pages.forEach { it.source.recycle() }
        singles.values.forEach { it.recycle() }
        pages.clear()
        singles.clear()
        skinNames.clear()

        val prefix = keyPrefix
        val unload = { Textures.unloadAll { key, _ -> key.startsWith(prefix) } }

        if (VerktexBridge.isUpdateThread) {
            unload()
        } else {
            Verktex.scheduleOnUpdate(unload)
        }

        currentPage = 0
        currentX = 0
        currentY = 0
        lineMaxY = 0
    }

    private fun isSkinTexture(name: String): Boolean {
        val resources = ResourceManager.getInstance()
        val skinName = name.substringBeforeLast('.')

        if (!resources.isTextureLoaded(skinName)) {
            return false
        }

        return resources.getTexture(skinName) != null
    }

    /**
     * Resolves the file of a texture. Some old storyboards do not include a file extension, in
     * which case the supported image extensions are tried, as in osu!lazer.
     */
    private fun resolve(name: String): File {
        val file = File(directory, name)

        if (file.extension.isEmpty() && !file.exists()) {
            for (extension in IMAGE_EXTENSIONS) {
                val candidate = File(directory, name + extension)

                if (candidate.exists()) {
                    return candidate
                }
            }
        }

        return file
    }

    private fun add(name: String) {
        val file = resolve(name)

        // A missing or corrupt file is not registered, it draws the shared placeholder.
        val bitmap = decodeBitmap(file) ?: return

        singles[name] = StoryboardTextureSource(bitmap) { loadBitmap(file) }
    }

    private fun packAll(names: List<String>) {
        val infos = names.map { loadInfo(it) }
            .sortedWith(compareBy({ it.height }, { it.width }))

        val toPack = mutableListOf<TextureInfo>()

        for (info in infos) {
            if (info.width > MAX_PACKED_SIZE || info.height > MAX_PACKED_SIZE) {
                // Oversized textures are loaded individually.
                add(info.name)
            } else {
                placeInPack(info)
                toPack.add(info)
            }
        }

        if (toPack.isEmpty()) {
            return
        }

        val byPage = toPack.groupBy { it.pageIndex }

        // The first (and possibly only) page can be shrunk to the used height.
        val singlePageHeight = if (byPage.size == 1) lineMaxY + 10 else PAGE_SIZE
        val pageHeight = singlePageHeight.coerceAtMost(PAGE_SIZE)

        // One bitmap per page: Verktex uploads from the source when the page is first bound, right after register().
        for ((_, pageInfos) in byPage) {
            val source = StoryboardTextureSource(composePage(pageInfos, pageHeight)) { composePage(pageInfos, pageHeight) }

            pages.add(Page(source, pageInfos))
        }
    }

    private fun composePage(pageInfos: List<TextureInfo>, pageHeight: Int): Bitmap {
        val pageBitmap = Bitmap.createBitmap(
            PAGE_SIZE,
            pageHeight,
            Bitmap.Config.ARGB_8888
        )
        val canvas = Canvas(pageBitmap)
        val paint = Paint().also {
            it.isAntiAlias = true
            it.xfermode = PorterDuffXfermode(PorterDuff.Mode.SRC)
        }

        pageBitmap.eraseColor(Color.argb(0, 0, 0, 0))

        for (info in pageInfos) {
            val bitmap = loadBitmap(resolve(info.name))
            canvas.drawBitmap(bitmap, info.x.toFloat(), info.y.toFloat(), paint)
            bitmap.recycle()
        }

        return pageBitmap
    }

    private fun placeInPack(info: TextureInfo) {
        if (currentX + info.width + MARGIN >= PAGE_SIZE) {
            currentX = 0
            currentY = lineMaxY + MARGIN
        }

        if (currentY + info.height + MARGIN >= PAGE_SIZE) {
            ++currentPage
            currentX = 0
            currentY = 0
            lineMaxY = 0
        }

        info.pageIndex = currentPage
        info.x = currentX
        info.y = currentY
        currentX += info.width + MARGIN
        lineMaxY = maxOf(lineMaxY, currentY + info.height + MARGIN)
    }

    private fun loadInfo(name: String) = TextureInfo(name).also {
        try {
            val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(resolve(name).absolutePath, options)

            if (options.outWidth > 0 && options.outHeight > 0) {
                it.width = options.outWidth
                it.height = options.outHeight
            }
        } catch (_: Exception) {
        }
    }

    private fun loadBitmap(file: File): Bitmap = decodeBitmap(file) ?: errorBitmap()

    private fun decodeBitmap(file: File): Bitmap? {
        if (!file.isFile) {
            return null
        }

        return try {
            BitmapFactory.decodeFile(file.absolutePath, bitmapOptions)
        } catch (_: Exception) {
            null
        }
    }

    /**
     * The region of a texture all pools share, created on first use and kept. [create] must not capture a
     * pool, the shared source keeps it for its reloads.
     */
    private fun shared(key: String, create: () -> Bitmap): GraphicsTexture.Region =
        Textures[key] ?: checkNotNull(Textures.load(key, StoryboardTextureSource(create(), create), standalone = true))

    private class TextureInfo(@JvmField val name: String) {
        @JvmField var width = 1
        @JvmField var height = 1
        @JvmField var x = 0
        @JvmField var y = 0
        @JvmField var pageIndex = -1
    }

    private class Page(@JvmField val source: StoryboardTextureSource, @JvmField val infos: List<TextureInfo>)

    companion object {
        /**
         * The minimum number of references a texture needs to be packed into a shared atlas.
         */
        private const val PACK_USAGE_THRESHOLD = 15

        private const val PAGE_SIZE = 2048
        private const val MAX_PACKED_SIZE = 400
        private const val MARGIN = 2

        private val IMAGE_EXTENSIONS = arrayOf(".jpeg", ".jpg", ".png")

        private const val KEY_PREFIX = "storyboard:"

        // Shared by all pools and outside every pool's prefix, so clear() keeps them.
        private const val PLACEHOLDER_KEY = "storyboard:<placeholder>"
        private const val WHITE_KEY = "storyboard:white"

        private val nextPoolId = AtomicInteger()

        private fun errorBitmap(): Bitmap = Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888).also {
            it.setPixel(0, 0, Color.argb(255, 255, 0, 0))
        }

        private fun whiteBitmap(): Bitmap = Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888).also {
            it.setPixel(0, 0, Color.argb(255, 255, 255, 255))
        }
    }
}
