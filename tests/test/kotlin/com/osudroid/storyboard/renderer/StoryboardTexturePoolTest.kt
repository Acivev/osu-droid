package com.osudroid.storyboard.renderer

import android.graphics.Bitmap
import com.osudroid.storyboard.model.Storyboard
import com.osudroid.storyboard.model.StoryboardLayerType
import com.osudroid.storyboard.model.StoryboardOrigin
import com.osudroid.storyboard.model.StoryboardSprite
import com.osudroid.verktex.VerktexTestFixtures
import com.reco1l.verktex.Platform
import com.reco1l.verktex.texture.Textures
import java.io.File
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * The pool keeps its shelf packing on top of Verktex textures: frequent small textures share page
 * textures, everything else is a standalone texture, and every key of a pool goes with clear().
 */
@RunWith(RobolectricTestRunner::class)
class StoryboardTexturePoolTest {

    @get:Rule
    val temporaryFolder = TemporaryFolder()

    private lateinit var directory: File

    @Before
    fun setUp() {
        VerktexTestFixtures.ensureInitialized(maxTextureSize = 4096f)
        directory = temporaryFolder.newFolder("beatmap")
    }

    @After
    fun tearDown() {
        Textures.unloadAll()
    }

    private fun png(name: String, width: Int, height: Int) {
        File(directory, name).outputStream().use {
            Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888).compress(Bitmap.CompressFormat.PNG, 100, it)
        }
    }

    private fun storyboardOf(vararg uses: Pair<String, Int>) = Storyboard().also {
        for ((name, count) in uses) {
            repeat(count) { _ -> it.add(StoryboardSprite(StoryboardLayerType.Foreground, StoryboardOrigin.Centre, name, 320f, 240f)) }
        }
    }

    private fun registered(storyboard: Storyboard) = StoryboardTexturePool(directory).also {
        it.load(storyboard)
        it.register()
    }

    // The scheduled tasks run before the update loop, which needs fonts the fixtures do not load.
    private fun pumpUpdate() {
        runCatching { (Platform.implementation as VerktexTestFixtures.FakePlatform).workers.getValue("update_worker").pump() }
    }

    @Test
    fun frequentTexturesArePacked() {
        png("a.png", 10, 10)
        png("b.png", 12, 12)

        val pool = registered(storyboardOf("a.png" to 15, "b.png" to 14))

        val page = Textures[pool.keyPrefix + "page0"]
        assertNotNull(page)
        assertSame(page!!.texture, pool.get("a.png").texture)
        assertEquals(10f, pool.get("a.png").width)

        assertSame(Textures[pool.keyPrefix + "b.png"], pool.get("b.png"))
        assertEquals(12f, pool.get("b.png").texture.width)
    }

    @Test
    fun oversizedTexturesStaySingle() {
        png("big.png", 401, 20)

        val pool = registered(storyboardOf("big.png" to 15))

        assertSame(Textures[pool.keyPrefix + "big.png"], pool.get("big.png"))
        assertFalse(pool.keyPrefix + "page0" in Textures)
    }

    @Test
    fun placementUsesMarginTwoAndWrapsAt2048() {
        val names = (0 until 6).map { "wide$it.png" }
        names.forEach { png(it, 400, 10) }

        val pool = registered(storyboardOf(*names.map { it to 15 }.toTypedArray()))

        val positions = names.map { pool.get(it).let { region -> region.x to region.y } }.toSet()

        // Five fit on a row with a margin of 2; the sixth wraps below the row's bottom plus its margin.
        assertEquals(setOf(0f to 0f, 402f to 0f, 804f to 0f, 1206f to 0f, 1608f to 0f, 0f to 14f), positions)

        // A single page is shrunk to the used height plus 10.
        val page = pool.get(names[0]).texture
        assertEquals(2048f, page.width)
        assertEquals(36f, page.height)
    }

    @Test
    fun missingFileIsThePlaceholder() {
        val pool = registered(storyboardOf("missing.png" to 1))

        val region = pool.get("missing.png")
        assertEquals(1f, region.width)
        assertEquals(1f, region.height)
        assertSame(pool.get("never-referenced.png"), region)
    }

    @Test
    fun sourceAboveTheMaxTextureSizeIsThePlaceholderAndRecycled() {
        // The fixture's device allows 4096 pixels.
        png("huge.png", 4097, 1)

        val pool = StoryboardTexturePool(directory)
        pool.load(storyboardOf("huge.png" to 1))

        @Suppress("UNCHECKED_CAST")
        val singles = StoryboardTexturePool::class.java.getDeclaredField("singles").apply { isAccessible = true }.get(pool) as Map<String, StoryboardTextureSource>
        val source = singles.getValue("huge.png")
        val bitmap = StoryboardTextureSource::class.java.getDeclaredField("bitmap").apply { isAccessible = true }.get(source) as Bitmap

        pool.register()

        val region = pool.get("huge.png")
        assertSame(pool.get("never-referenced.png"), region)
        assertEquals(1f, region.width)
        assertFalse(pool.keyPrefix + "huge.png" in Textures)
        assertTrue(bitmap.isRecycled)
    }

    @Test
    fun extensionlessNameFindsThePng() {
        png("sprite.png", 30, 20)

        val pool = registered(storyboardOf("sprite" to 1))

        assertEquals(30f, pool.get("sprite").width)
        assertEquals(20f, pool.get("sprite").height)
    }

    @Test
    fun unknownNameAtDrawTimeIsThePlaceholder() {
        png("known.png", 8, 8)

        val pool = registered(storyboardOf("known.png" to 1))
        val keys = Textures.getTextureKeys().toSet()

        val region = pool.get("unknown.png")

        assertEquals(1f, region.width)
        // Nothing is loaded at draw time.
        assertEquals(keys, Textures.getTextureKeys().toSet())
    }

    @Test
    fun onePageBitmapPerPage() {
        // 25 textures of 400 x 400 fill a page (five rows of five), the 26th starts the next page.
        png("square0.png", 400, 400)
        val bytes = File(directory, "square0.png").readBytes()
        val names = (0 until 26).map { "square$it.png" }
        names.drop(1).forEach { File(directory, it).writeBytes(bytes) }

        val pool = registered(storyboardOf(*names.map { it to 15 }.toTypedArray()))

        val first = Textures[pool.keyPrefix + "page0"]!!
        val second = Textures[pool.keyPrefix + "page1"]!!
        assertNotSame(first.texture, second.texture)
        assertNotSame(first.source, second.source)
        assertEquals(2048f, first.texture.height)
        assertEquals(2048f, second.texture.height)

        val textures = names.map { pool.get(it).texture }
        assertEquals(25, textures.count { it === first.texture })
        assertEquals(1, textures.count { it === second.texture })
        assertFalse(pool.keyPrefix + "page2" in Textures)
    }

    @Test
    fun registerUploadsOnTheNextDrawFrame() {
        png("a.png", 10, 10)
        png("b.png", 12, 12)

        val pool = StoryboardTexturePool(directory)
        pool.load(storyboardOf("a.png" to 15, "b.png" to 1))

        @Suppress("UNCHECKED_CAST")
        val singles = StoryboardTexturePool::class.java.getDeclaredField("singles").apply { isAccessible = true }.get(pool) as Map<String, StoryboardTextureSource>
        val pages = StoryboardTexturePool::class.java.getDeclaredField("pages").apply { isAccessible = true }.get(pool) as List<*>
        val pageSourceField = pages.first()!!.javaClass.getDeclaredField("source").apply { isAccessible = true }
        val sources = singles.values + pages.map { pageSourceField.get(it) as StoryboardTextureSource }
        assertEquals(2, sources.size)

        val bitmapField = StoryboardTextureSource::class.java.getDeclaredField("bitmap").apply { isAccessible = true }
        val bitmaps = sources.map { bitmapField.get(it) as Bitmap }

        pool.register()

        // Nothing is uploaded on the update thread.
        assertTrue(bitmaps.none { it.isRecycled })

        // No component draws here: the frame after register() binds the textures, which uploads and recycles them.
        VerktexTestFixtures.pumpDraw()

        assertTrue(bitmaps.all { it.isRecycled })
        assertTrue(sources.all { bitmapField.get(it) == null })
    }

    @Test
    fun clearUnloadsEveryPoolKey() {
        png("a.png", 10, 10)
        png("b.png", 12, 12)

        val pool = registered(storyboardOf("a.png" to 15, "b.png" to 1))
        val other = registered(storyboardOf("b.png" to 1))
        assertTrue(Textures.getTextureKeys().count { it.startsWith(pool.keyPrefix) } >= 2)

        // Off the update thread the unload is scheduled on it.
        pool.clear()
        pumpUpdate()

        assertEquals(0, Textures.getTextureKeys().count { it.startsWith(pool.keyPrefix) })
        // Another pool's textures stay.
        assertTrue(other.keyPrefix + "b.png" in Textures)
    }
}
