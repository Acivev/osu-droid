package com.osudroid.verktex.results

import com.osudroid.verktex.VerktexTestFixtures
import com.osudroid.verktex.VerktexTestFixtures.FakeTexture
import com.reco1l.verktex.texture.Textures
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import ru.nsu.ccfit.zuev.osu.scoring.ScoringScene

class ScoringSceneBackgroundTest {

    @After fun tearDown() {
        Textures.unload(ScoringScene.BACKGROUND_KEY)
        VerktexTestFixtures.pumpDraw()
    }

    @Test fun loadingTwiceReleasesThePreviousRegion() {
        val provider = VerktexTestFixtures.ensureInitialized()
        provider.textureSizes["bg-a.png"] = 1920f to 1080f
        provider.textureSizes["bg-b.png"] = 1280f to 720f

        val first = ScoringScene.loadResultsBackground("bg-a.png")!!
        val second = ScoringScene.loadResultsBackground("bg-b.png")!!
        VerktexTestFixtures.pumpDraw()

        assertTrue((first.texture as FakeTexture).disposed)
        assertFalse((second.texture as FakeTexture).disposed)
        assertEquals(1280f, second.texture.width)
    }

    @Test fun missingOrNullFileGivesNull() {
        VerktexTestFixtures.ensureInitialized()

        assertNull(ScoringScene.loadResultsBackground(null))
        assertNull(ScoringScene.loadResultsBackground("missing.png"))
    }
}
