package com.osudroid.storyboard.renderer

import android.graphics.Bitmap
import com.reco1l.verktex.android.AndroidTextureSource
import com.reco1l.verktex.graphics.GraphicsTexture

/**
 * A storyboard texture's pixels, uploaded through [AndroidTextureSource] but not kept in memory.
 *
 * The decoded bitmap is held until the first upload and recycled right after it, as the AndEngine
 * pool recycled its bitmaps once they were handed over. A later upload, which only happens after
 * the graphics context was lost, decodes the pixels again through [reload] (a single from its
 * file, a page by composing its files again) on the draw thread, as AndEngine reloaded from its
 * temporary files.
 */
class StoryboardTextureSource internal constructor(
    bitmap: Bitmap,

    /**
     * Decodes the pixels again, at the same size.
     */
    private val reload: () -> Bitmap,

    /**
     * Uploads a bitmap at a position of the bound texture, the seam the tests use.
     */
    private val upload: (Bitmap, Int, Int) -> Unit
) : GraphicsTexture.Source {

    constructor(bitmap: Bitmap, reload: () -> Bitmap) : this(bitmap, reload, { uploaded, x, y ->
        AndroidTextureSource(uploaded).uploadToHardware(x.toFloat(), y.toFloat())
    })

    private var bitmap: Bitmap? = bitmap

    override val width = bitmap.width.toFloat()
    override val height = bitmap.height.toFloat()

    override fun uploadToHardware(texturePositionX: Float, texturePositionY: Float) {
        val bitmap = this.bitmap ?: reload()
        this.bitmap = null

        try {
            upload(bitmap, texturePositionX.toInt(), texturePositionY.toInt())
        } finally {
            bitmap.recycle()
        }
    }

    /**
     * Recycles the bitmap of a source that was never handed to a texture.
     */
    fun recycle() {
        bitmap?.recycle()
        bitmap = null
    }
}
