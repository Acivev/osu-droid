package com.osudroid.verktex

import com.reco1l.verktex.Verktex
import org.junit.Assert.assertEquals
import org.junit.Test

class VerktexFixturesSmokeTest {
    @Test
    fun engineInitializesWithFakes() {
        VerktexTestFixtures.ensureInitialized()
        assertEquals("fake", Verktex.graphics.version)
        assertEquals(4096f, Verktex.graphics.device.capabilities.maxTextureSize)
    }
}
