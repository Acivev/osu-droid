package ru.nsu.ccfit.zuev.osu.helper

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MathUtilsTest {
    @Test
    fun `distance of a 3-4-5 triangle is 5`() {
        assertEquals(5f, MathUtils.distance(0f, 0f, 3f, 4f), 0f)
    }

    @Test
    fun `random with equal bounds returns that bound`() {
        assertEquals(1, MathUtils.random(1, 1))
    }

    @Test
    fun `random stays within inclusive bounds`() {
        repeat(100) {
            assertTrue(MathUtils.random(1, 3) in 1..3)
        }
    }
}
