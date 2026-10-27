package com.osudroid.verktex

import android.graphics.Bitmap
import com.reco1l.verktex.android.AndroidTextureSource
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Textures are stored with straight alpha, so premultiplied bitmaps are un-premultiplied on upload.
 */
@RunWith(RobolectricTestRunner::class)
class AndroidTextureSourceTest {

    private fun assertChannelsNear(expected: Int, actual: Int) {
        for (shift in intArrayOf(24, 16, 8, 0)) {
            val e = (expected ushr shift) and 0xFF
            val a = (actual ushr shift) and 0xFF
            assertTrue("channel at $shift: expected $e, got $a", kotlin.math.abs(e - a) <= 1)
        }
    }

    @Test
    fun unpremultiplyLeavesOpaquePixelsAndClearsTransparentOnes() {
        assertEquals(0xFFFF8000.toInt(), AndroidTextureSource.unpremultiply(0xFFFF8000.toInt()))
        assertEquals(0, AndroidTextureSource.unpremultiply(0x00123456))
        assertChannelsNear(0x80FF0000.toInt(), AndroidTextureSource.unpremultiply(0x80800000.toInt()))
    }

    @Test
    fun unpremultiplyNeverExceeds255() {
        for (alpha in 1..255) {
            // Channels larger than alpha are invalid premultiplied data, they must still clamp.
            val argb = (alpha shl 24) or 0xFFFFFF
            val result = AndroidTextureSource.unpremultiply(argb)
            assertEquals(alpha, result ushr 24)
            assertEquals(0xFFFFFF, result and 0xFFFFFF)
        }
    }

    @Test
    fun uploadDoesNotModifyTheBitmap() {
        val bitmap = Bitmap.createBitmap(2, 2, Bitmap.Config.ARGB_8888)
        val pixels = intArrayOf(0xFFFF8000.toInt(), 0x80800000.toInt(), 0x00000000, 0x40202020)
        bitmap.setPixels(pixels, 0, 2, 0, 0, 2, 2)

        val before = IntArray(4)
        bitmap.getPixels(before, 0, 2, 0, 0, 2, 2)

        try {
            AndroidTextureSource(bitmap).uploadToHardware(0f, 0f)
        } catch (_: UnsatisfiedLinkError) {
        } catch (_: RuntimeException) {
        }

        val after = IntArray(4)
        bitmap.getPixels(after, 0, 2, 0, 0, 2, 2)
        assertArrayEquals(before, after)
    }
}
