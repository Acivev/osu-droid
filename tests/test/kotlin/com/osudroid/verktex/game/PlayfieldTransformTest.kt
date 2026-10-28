package com.osudroid.verktex.game

import com.osudroid.game.PlayfieldTransform
import com.osudroid.math.Interpolation
import org.junit.Assert.assertEquals
import org.junit.Test

class PlayfieldTransformTest {

    private val transform = FloatArray(3)

    private fun compute(size: Float, horizontal: Float, vertical: Float) =
        PlayfieldTransform.compute(size, horizontal, vertical, RES_WIDTH, RES_HEIGHT, WINDOW_WIDTH, WINDOW_HEIGHT, UNIT_SCALE, transform)

    /** The camera centre of GameScene.applyPlayfieldSizeScale for one axis. */
    private fun cameraCentre(size: Float, position: Float, res: Float) = res * Interpolation.linear(
        Interpolation.linear(0f, 0.5f, size),
        Interpolation.linear(1f, 0.5f, size),
        1 - position
    )

    @Test
    fun identityAtFullSize() {
        compute(1f, 0.5f, 0.5f)
        assertEquals(2f, transform[0], EPSILON)
        assertEquals(0f, transform[1], EPSILON)
        assertEquals(0f, transform[2], EPSILON)
    }

    @Test
    fun centredAtPointEight() {
        compute(0.8f, 0.5f, 0.5f)
        assertEquals(1.6f, transform[0], EPSILON)
        assertEquals(256f, transform[1], EPSILON)
        assertEquals(144f, transform[2], EPSILON)
    }

    @Test
    fun cornerPositions() {
        compute(0.8f, 0f, 0f)
        assertEquals(1.6f, transform[0], EPSILON)
        assertEquals(RES_WIDTH * 0.6f, cameraCentre(0.8f, 0f, RES_WIDTH), EPSILON)
        assertEquals(RES_HEIGHT * 0.6f, cameraCentre(0.8f, 0f, RES_HEIGHT), EPSILON)
        assertEquals(WINDOW_WIDTH / 2 - cameraCentre(0.8f, 0f, RES_WIDTH) * 1.6f, transform[1], EPSILON)
        assertEquals(WINDOW_HEIGHT / 2 - cameraCentre(0.8f, 0f, RES_HEIGHT) * 1.6f, transform[2], EPSILON)

        compute(0.8f, 1f, 1f)
        assertEquals(RES_WIDTH * 0.4f, cameraCentre(0.8f, 1f, RES_WIDTH), EPSILON)
        assertEquals(RES_HEIGHT * 0.4f, cameraCentre(0.8f, 1f, RES_HEIGHT), EPSILON)
        assertEquals(WINDOW_WIDTH / 2 - cameraCentre(0.8f, 1f, RES_WIDTH) * 1.6f, transform[1], EPSILON)
        assertEquals(WINDOW_HEIGHT / 2 - cameraCentre(0.8f, 1f, RES_HEIGHT) * 1.6f, transform[2], EPSILON)
    }

    @Test
    fun roundTrip() {
        compute(0.8f, 0.3f, 0.7f)
        val playfield = FloatArray(2)
        val window = FloatArray(2)

        for ((x, y) in listOf(0f to 0f, 640f to 360f, 2560f to 1440f)) {
            PlayfieldTransform.toPlayfield(x, y, transform, playfield)
            PlayfieldTransform.toWindow(playfield[0], playfield[1], transform, window)
            assertEquals(x, window[0], 1e-2f)
            assertEquals(y, window[1], 1e-2f)
        }

        // The window centre shows the camera centre.
        PlayfieldTransform.toPlayfield(WINDOW_WIDTH / 2, WINDOW_HEIGHT / 2, transform, playfield)
        assertEquals(cameraCentre(0.8f, 0.3f, RES_WIDTH), playfield[0], 1e-2f)
        assertEquals(cameraCentre(0.8f, 0.7f, RES_HEIGHT), playfield[1], 1e-2f)
    }

    private companion object {
        const val RES_WIDTH = 1280f
        const val RES_HEIGHT = 720f
        const val WINDOW_WIDTH = 2560f
        const val WINDOW_HEIGHT = 1440f
        const val UNIT_SCALE = 2f
        const val EPSILON = 1e-3f
    }
}
