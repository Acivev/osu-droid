package com.osudroid.verktex.hud

import com.osudroid.ui.v2.hud.HUDSkinData
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

// Robolectric for the real org.json, android.jar only has stubs of it.
@RunWith(RobolectricTestRunner::class)
class HUDSkinDataTest {

    @Test
    fun skinDataRoundTripsThroughJson() {
        val json = HUDSkinData.writeToJSON(HUDSkinData.Default)
        val parsed = HUDSkinData.readFromJSON(json)

        assertEquals(HUDSkinData.Default.elements.size, parsed.elements.size)

        HUDSkinData.Default.elements.zip(parsed.elements).forEach { (expected, actual) ->
            assertEquals(expected.type, actual.type)
            assertEquals(expected.anchor, actual.anchor)
            assertEquals(expected.origin, actual.origin)
            assertEquals(expected.position.x, actual.position.x, 1e-4f)
            assertEquals(expected.position.y, actual.position.y, 1e-4f)
            assertEquals(expected.scale, actual.scale, 1e-4f)
        }

        assertEquals(HUDSkinData.Default, parsed)
    }
}
