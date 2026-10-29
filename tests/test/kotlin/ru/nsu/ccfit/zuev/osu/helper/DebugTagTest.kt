package ru.nsu.ccfit.zuev.osu.helper

import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.shadows.ShadowLog

@RunWith(RobolectricTestRunner::class)
class DebugTagTest {
    @Test
    fun `e logs under the AndEngine tag`() {
        ShadowLog.clear()
        Debug.e("x")

        val item = ShadowLog.getLogs().single()
        assertEquals("AndEngine", item.tag)
        assertEquals("x", item.msg)
    }

    @Test
    fun `i with a throwable logs under the AndEngine tag`() {
        ShadowLog.clear()
        val error = IllegalStateException("boom")
        Debug.i("y", error)

        val item = ShadowLog.getLogs().single()
        assertEquals("AndEngine", item.tag)
        assertEquals(error, item.throwable)
    }
}
