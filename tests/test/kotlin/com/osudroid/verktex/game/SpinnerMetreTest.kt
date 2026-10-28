package com.osudroid.verktex.game

import org.junit.Assert.assertEquals
import org.junit.Test
import ru.nsu.ccfit.zuev.osu.game.GameplaySpinner

/**
 * The per-frame crop of the classic spinner's metre region: its height and y from the fill.
 */
class SpinnerMetreTest {

    private val out = FloatArray(2)

    private fun crop(percentFill: Float, fillOffset: Float): Pair<Float, Float> {
        GameplaySpinner.metreCrop(400f, percentFill, fillOffset, out)
        return out[0] to out[1]
    }

    @Test fun metreCropMatchesTheFormula() {
        assertEquals(0f to 400f, crop(0f, 1f))
        assertEquals(200f to 200f, crop(0.5f, 0.5f))
        assertEquals(400f to 0f, crop(1.2f, 0f))
    }
}
