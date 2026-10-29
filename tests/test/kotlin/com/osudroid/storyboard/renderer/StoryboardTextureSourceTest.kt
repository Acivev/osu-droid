package com.osudroid.storyboard.renderer

import android.graphics.Bitmap
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * A storyboard texture keeps its bitmap only until the first upload; a later upload (after a context loss)
 * decodes it again.
 */
@RunWith(RobolectricTestRunner::class)
class StoryboardTextureSourceTest {

    private val uploads = mutableListOf<Triple<Bitmap, Int, Int>>()
    private var reloads = 0

    private fun source(bitmap: Bitmap) = StoryboardTextureSource(
        bitmap,
        reload = {
            reloads++
            Bitmap.createBitmap(4, 2, Bitmap.Config.ARGB_8888)
        },
        upload = { uploaded, x, y ->
            // The bitmap is uploaded before it is recycled.
            assertFalse(uploaded.isRecycled)
            uploads += Triple(uploaded, x, y)
        }
    )

    @Test
    fun recyclesAfterTheFirstUpload() {
        val bitmap = Bitmap.createBitmap(4, 2, Bitmap.Config.ARGB_8888)
        val source = source(bitmap)

        assertEquals(4f, source.width)
        assertEquals(2f, source.height)

        source.uploadToHardware(3f, 5f)

        assertEquals(1, uploads.size)
        assertSame(bitmap, uploads[0].first)
        assertEquals(3, uploads[0].second)
        assertEquals(5, uploads[0].third)
        assertTrue(bitmap.isRecycled)
        assertEquals(0, reloads)

        // The size stays known without the bitmap.
        assertEquals(4f, source.width)
    }

    @Test
    fun reloadsForASecondUpload() {
        val source = source(Bitmap.createBitmap(4, 2, Bitmap.Config.ARGB_8888))

        source.uploadToHardware(0f, 0f)
        source.uploadToHardware(0f, 0f)

        assertEquals(1, reloads)
        assertEquals(2, uploads.size)
        assertNotSame(uploads[0].first, uploads[1].first)
        assertTrue(uploads[1].first.isRecycled)
    }
}
