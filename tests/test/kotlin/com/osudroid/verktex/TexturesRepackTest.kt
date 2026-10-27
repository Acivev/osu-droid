package com.osudroid.verktex

import com.osudroid.verktex.VerktexTestFixtures.FakeSource
import com.osudroid.verktex.VerktexTestFixtures.FakeTexture
import com.reco1l.verktex.graphics.GraphicsTexture
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class TexturesRepackTest {

    @Before
    fun setUp() {
        VerktexTestFixtures.ensureInitialized(maxTextureSize = 4096f)
    }

    /**
     * The sources a 2048² atlas held on device after switching the skin to OwO and opening the song
     * menu, in the order they were added. Removing the first one crashed the next skin switch.
     */
    private val skinSwitchSizes = listOf(
        586 to 92, 586 to 92, 586 to 92, 586 to 92, 586 to 92, 583 to 89, 80 to 284, 512 to 3,
        540 to 540, 1456 to 1232, 66 to 66, 60 to 62, 64 to 62, 64 to 62, 1 to 1, 1366 to 75,
        212 to 212
    ) + List(15) { 225 to 233 } + listOf(77 to 90, 77 to 90, 77 to 90, 740 to 120, 40 to 46)

    private fun fill(atlas: GraphicsTexture): List<GraphicsTexture.Region> =
        skinSwitchSizes.map { (w, h) -> atlas.addSource(FakeSource(w.toFloat(), h.toFloat()))!! }

    private fun assertValidLayout(atlas: GraphicsTexture, regions: List<GraphicsTexture.Region>) {
        for (r in regions) {
            assertTrue(
                "Region ${r.width}x${r.height} at ${r.x},${r.y} is outside the atlas",
                r.x >= 0f && r.y >= 0f && r.x + r.width <= atlas.width && r.y + r.height <= atlas.height
            )
        }
        for (i in regions.indices) for (j in i + 1 until regions.size) {
            val a = regions[i]
            val b = regions[j]
            val overlaps = a.x < b.x + b.width && b.x < a.x + a.width && a.y < b.y + b.height && b.y < a.y + a.height
            assertFalse("Regions $i and $j overlap", overlaps)
        }
    }

    @Test
    fun removingARegionKeepsTheRestPackedWhenARepackWouldNotFit() {
        val atlas = FakeTexture(2048f, 2048f)
        val regions = fill(atlas)

        // Same placement as on device.
        assertEquals(540f, regions[9].x)
        assertEquals(376f, regions[9].y)

        val before = regions.drop(1).map { it.x to it.y }

        atlas.removeRegion(regions[0])

        // A repack that fit would move the survivors; unchanged positions mean the fallback ran.
        assertEquals(before, regions.drop(1).map { it.x to it.y })
        assertValidLayout(atlas, regions.drop(1))
    }

    @Test
    fun freedSpaceCanBeReused() {
        val atlas = FakeTexture(2048f, 2048f)
        val regions = fill(atlas)

        atlas.removeRegion(regions[0])
        val again = atlas.addSource(FakeSource(586f, 92f))

        assertNotNull(again)
        assertValidLayout(atlas, regions.drop(1) + again!!)
    }

    @Test
    fun removingEverySkinRegionOneByOneNeverFails() {
        val atlas = FakeTexture(2048f, 2048f)
        val regions = fill(atlas).toMutableList()

        while (regions.isNotEmpty()) {
            atlas.removeRegion(regions.removeAt(0))
            assertValidLayout(atlas, regions)
        }

        assertNotNull(atlas.addSource(FakeSource(2048f, 2048f)))
    }
}
