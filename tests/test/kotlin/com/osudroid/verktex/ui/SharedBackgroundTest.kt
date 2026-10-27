package com.osudroid.verktex.ui

import com.osudroid.ui.verktex.SharedBackground
import com.osudroid.verktex.VerktexTestFixtures
import com.osudroid.verktex.VerktexTestFixtures.FakeTexture
import com.reco1l.verktex.texture.Textures
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class SharedBackgroundTest {

    private val defaultFileProvider = SharedBackground.fileProvider
    private var file: String? = null

    @Before fun setUp() {
        val provider = VerktexTestFixtures.ensureInitialized()
        provider.textureSizes["bg-a.png"] = 1920f to 1080f
        provider.textureSizes["bg-b.png"] = 1280f to 720f
        SharedBackground.fileProvider = { file }
    }

    @After fun tearDown() {
        SharedBackground.fileProvider = defaultFileProvider
        SharedBackground.invalidate()
        Textures.unload(SharedBackground.KEY)
        VerktexTestFixtures.pumpDraw()
    }

    @Test fun sameFileIsDecodedOnce() {
        file = "bg-a.png"

        val first = SharedBackground.region()!!
        val second = SharedBackground.region()!!
        VerktexTestFixtures.pumpDraw()

        assertSame(first, second)
        assertSame(first.texture, second.texture)
        assertFalse((first.texture as FakeTexture).disposed)
    }

    @Test fun changedFileReleasesThePrevious() {
        file = "bg-a.png"
        val first = SharedBackground.region()!!
        file = "bg-b.png"
        val second = SharedBackground.region()!!
        VerktexTestFixtures.pumpDraw()

        assertTrue((first.texture as FakeTexture).disposed)
        assertFalse((second.texture as FakeTexture).disposed)
        assertEquals(1280f, second.texture.width)
    }

    @Test fun missingOrNullFileGivesNull() {
        file = null
        assertNull(SharedBackground.region())

        file = "missing.png"
        assertNull(SharedBackground.region())
    }

    @Test fun invalidateReDecodesTheSameFile() {
        file = "bg-a.png"
        val first = SharedBackground.region()!!
        SharedBackground.invalidate()
        val second = SharedBackground.region()!!
        VerktexTestFixtures.pumpDraw()

        assertNotSame(first.texture, second.texture)
        assertTrue((first.texture as FakeTexture).disposed)
    }
}
