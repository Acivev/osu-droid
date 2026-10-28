package com.osudroid.game

import com.osudroid.math.Interpolation

/**
 * The playfield camera of `GameScene.applyPlayfieldSizeScale` as a scale and an offset from
 * playfield units (the RES space) to window pixels. Pure, the results go into `out` arrays so a
 * frame does not allocate.
 *
 * A transform is a float array holding the scale, the x offset and the y offset, in that order.
 */
object PlayfieldTransform {

    /**
     * Computes the transform into [out] (scale, offsetX, offsetY): scale = [unitScale] × [playfieldSize];
     * offset = window centre − camera centre × scale, with the camera centre of
     * `GameScene.applyPlayfieldSizeScale`: `Interpolation.linear(Interpolation.linear(0, 0.5, size),
     * Interpolation.linear(1, 0.5, size), 1 − position) × RES` on each axis.
     *
     * @param unitScale Window pixels per playfield unit at full size.
     */
    @JvmStatic
    fun compute(
        playfieldSize: Float,
        horizontalPosition: Float,
        verticalPosition: Float,
        resWidth: Float,
        resHeight: Float,
        windowWidth: Float,
        windowHeight: Float,
        unitScale: Float,
        out: FloatArray
    ) {
        val scale = unitScale * playfieldSize

        out[0] = scale
        out[1] = windowWidth / 2f - cameraCentre(playfieldSize, horizontalPosition) * resWidth * scale
        out[2] = windowHeight / 2f - cameraCentre(playfieldSize, verticalPosition) * resHeight * scale
    }

    /**
     * Maps window pixels to playfield units with [transform] into [out]: (x − offsetX) / scale,
     * (y − offsetY) / scale.
     */
    @JvmStatic
    fun toPlayfield(x: Float, y: Float, transform: FloatArray, out: FloatArray) {
        out[0] = (x - transform[1]) / transform[0]
        out[1] = (y - transform[2]) / transform[0]
    }

    /**
     * Maps playfield units to window pixels with [transform] into [out]: x × scale + offsetX,
     * y × scale + offsetY.
     */
    @JvmStatic
    fun toWindow(x: Float, y: Float, transform: FloatArray, out: FloatArray) {
        out[0] = x * transform[0] + transform[1]
        out[1] = y * transform[0] + transform[2]
    }

    /** The camera centre on one axis as a fraction of the RES size. */
    private fun cameraCentre(playfieldSize: Float, position: Float) = Interpolation.linear(
        Interpolation.linear(0f, 0.5f, playfieldSize),
        Interpolation.linear(1f, 0.5f, playfieldSize),
        1 - position
    )
}
