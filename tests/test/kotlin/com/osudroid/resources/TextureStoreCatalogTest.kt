package com.osudroid.resources

import android.graphics.Bitmap
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.GraphicsMode

/**
 * The store is a catalog: a name is Present (decoded, with its size), Absent (checked, missing) or
 * unknown, and a file that does not decode is never stored.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class TextureStoreCatalogTest {

    @get:Rule
    val temporaryFolder = TemporaryFolder()

    private lateinit var store: TextureStore
    private lateinit var directory: File

    @Before
    fun setUp() {
        store = TextureStore()
        store.init(RuntimeEnvironment.getApplication())
        directory = temporaryFolder.newFolder("skin")
    }

    private fun png(name: String, width: Int, height: Int) = File(directory, name).also { file ->
        file.outputStream().use {
            Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888).compress(Bitmap.CompressFormat.PNG, 100, it)
        }
    }

    @Test
    fun presentAnswersTheEntryAbsentAnswersNull() {
        store.put("hitcircle", TextureEntry(TextureEntry.Source.SkinFile, null, 128, 128))
        store.markAbsent("lighting")

        assertNotNull(store.get("hitcircle"))
        assertNull(store.get("lighting"))
        assertTrue(store.containsKey("hitcircle"))
        assertTrue(store.containsKey("lighting"))
    }

    @Test
    fun corruptFileIsNotStored() {
        val file = File(directory, "hitcircle.png").also { it.writeBytes(ByteArray(10) { i -> i.toByte() }) }

        assertNull(store.load("hitcircle", file.path, true))
        assertFalse(store.containsKey("hitcircle"))
    }

    @Test
    fun missingFileIsNotStored() {
        assertNull(store.load("hitcircle", File(directory, "hitcircle.png").path, true))
        assertFalse(store.containsKey("hitcircle"))
    }

    @Test
    fun hdFileHalvesTheSize() {
        val hd = png("hitcircle@2x.png", 64, 64)

        val entry = store.load("hitcircle", File(directory, "hitcircle.png").path, true)

        assertNotNull(entry)
        assertEquals(32, entry!!.width)
        assertEquals(32, entry.height)
        assertEquals(hd, entry.file)
        assertEquals(TextureEntry.Source.SkinFile, entry.source)
        assertTrue(store.containsKey("hitcircle"))
    }

    @Test
    fun defaultAssetIsPresent() {
        val entry = store.load("hitcircle", "gfx/hitcircle.png", false)

        assertNotNull(entry)
        assertEquals(TextureEntry.Source.DefaultAsset, entry!!.source)
        assertTrue(entry.width > 0 && entry.height > 0)
        assertEquals(entry, store.get("hitcircle"))
    }

    @Test
    fun removeOfAbsentIsNull() {
        store.markAbsent("lighting")

        assertNull(store.remove("lighting"))
        assertFalse(store.containsKey("lighting"))
    }
}
