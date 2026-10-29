package com.osudroid.verktex.bridge

import org.junit.Assert.assertArrayEquals
import org.junit.Test

class GameSurfaceViewMeasureTest {

    @Test
    fun widerViewIsLetterboxedToTheRatio() {
        assertArrayEquals(intArrayOf(1920, 1080), RatioMeasure.measure(16f / 9f, 2400, 1080))
    }

    @Test
    fun tallerViewIsLetterboxedToTheRatio() {
        assertArrayEquals(intArrayOf(1600, 900), RatioMeasure.measure(16f / 9f, 1600, 1200))
    }

    @Test
    fun zeroRatioMeasuresMatchParent() {
        assertArrayEquals(intArrayOf(2400, 1080), RatioMeasure.measure(0f, 2400, 1080))
    }
}
