package com.osudroid.verktex.bridge

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class EglConfigLadderTest {

    private val es2 = 0x0004
    private val es3 = 0x0040

    @Test
    fun strictMatchWins() {
        val configs = listOf(
            intArrayOf(8, 8, 8, 8, 24, 0, es3),
            intArrayOf(8, 8, 8, 8, 24, 8, es2),
            intArrayOf(5, 6, 5, 0, 16, 0, es2),
            intArrayOf(8, 8, 8, 8, 24, 8, es2 or es3),
        )

        assertEquals(3, EglConfigLadder.pick(configs))
    }

    @Test
    fun looserStencilWinsOverLooserDepth() {
        val configs = listOf(
            intArrayOf(8, 8, 8, 8, 16, 8, es3),
            intArrayOf(8, 8, 8, 8, 24, 0, es2),
            intArrayOf(8, 8, 8, 8, 24, 0, es3),
        )

        assertEquals(2, EglConfigLadder.pick(configs))
    }

    @Test
    fun looserDepthAndStencilNeedAtLeastDepth16() {
        val configs = listOf(
            intArrayOf(8, 8, 8, 8, 0, 0, es3),
            intArrayOf(8, 8, 8, 8, 24, 8, es2),
            intArrayOf(8, 8, 8, 8, 16, 0, es3),
        )

        assertEquals(2, EglConfigLadder.pick(configs))
    }

    @Test
    fun chooserWalksTheLadderToAnyConfig() {
        assertEquals(0, EglConfigLadder.pick(listOf(intArrayOf(5, 6, 5, 0, 16, 0, es2))))
    }

    @Test
    fun noConfigsIsNull() {
        assertNull(EglConfigLadder.pick(emptyList()))
    }
}
