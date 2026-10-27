package com.osudroid.verktex.ui

import com.osudroid.ui.verktex.LegacyUnits
import org.junit.Assert.assertEquals
import org.junit.Test

class LegacyUnitsTest {
    @Test fun fontSizesMatchResourceManager() {
        assertEquals(28f, LegacyUnits.fontSize("font"))
        assertEquals(24f, LegacyUnits.fontSize("middleFont"))
        assertEquals(21f, LegacyUnits.fontSize("smallFont"))
        assertEquals(16f, LegacyUnits.fontSize("xs"))
        assertEquals(36f, LegacyUnits.fontSize("strokeFont"))
        assertEquals(36f, LegacyUnits.fontSize("bigFont"))
        assertEquals(35f, LegacyUnits.fontSize("CaptionFont"))
        assertEquals(35f, LegacyUnits.fontSize("unknown"))
    }
}
