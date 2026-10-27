package com.osudroid.verktex

import com.reco1l.verktex.texture.Textures
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class TexturesStandaloneTest {
    private lateinit var provider: VerktexTestFixtures.FakeProvider

    @Before
    fun setUp() {
        provider = VerktexTestFixtures.ensureInitialized(maxTextureSize = 4096f)
    }

    @After
    fun tearDown() {
        Textures.unloadAll()
    }

    @Test
    fun smallSourceGoesIntoAnAtlas() {
        provider.textureSizes["small.png"] = 100f to 100f
        val region = Textures.load("t-small", "small.png", isInternal = true)!!
        assertEquals(2048f, region.texture.width)
    }

    @Test
    fun oversizedSourceGetsOwnTexture() {
        provider.textureSizes["bg.png"] = 3000f to 1500f
        val region = Textures.load("t-bg", "bg.png", isInternal = true)!!
        assertEquals(3000f, region.texture.width)
        assertEquals(1500f, region.texture.height)
        assertEquals(0f, region.x)
        assertEquals(3000f, region.width)
    }

    @Test
    fun sourceBeyondDeviceLimitReturnsNull() {
        provider.textureSizes["huge.png"] = 5000f to 100f
        assertNull(Textures.load("t-huge", "huge.png", isInternal = true))
        assertFalse("t-huge" in Textures)
    }

    @Test
    fun unloadDisposesStandaloneTexture() {
        provider.textureSizes["bg2.png"] = 2500f to 1000f
        val region = Textures.load("t-bg2", "bg2.png", isInternal = true)!!
        Textures.unload("t-bg2")
        VerktexTestFixtures.pumpDraw()
        assertTrue((region.texture as VerktexTestFixtures.FakeTexture).disposed)
        assertFalse("t-bg2" in Textures)
    }

    @Test
    fun overwritingKeyDisposesPreviousStandaloneTexture() {
        provider.textureSizes["bg3a.png"] = 2500f to 1000f
        provider.textureSizes["bg3b.png"] = 2600f to 1000f
        val first = Textures.load("t-bg3", "bg3a.png", isInternal = true)!!
        val second = Textures.load("t-bg3", "bg3b.png", isInternal = true)!!
        VerktexTestFixtures.pumpDraw()
        assertTrue((first.texture as VerktexTestFixtures.FakeTexture).disposed)
        assertFalse((second.texture as VerktexTestFixtures.FakeTexture).disposed)
        assertSame(second, Textures["t-bg3"])
    }

    @Test
    fun failedOverwriteDoesNotKeepDeadRegion() {
        provider.textureSizes["bg4a.png"] = 2500f to 1000f
        provider.textureSizes["bg4huge.png"] = 5000f to 100f
        val first = Textures.load("t-bg4", "bg4a.png", isInternal = true)!!
        assertNull(Textures.load("t-bg4", "bg4huge.png", isInternal = true))
        assertFalse("t-bg4" in Textures)
        assertNull(Textures["t-bg4"])
        VerktexTestFixtures.pumpDraw()
        assertTrue((first.texture as VerktexTestFixtures.FakeTexture).disposed)
    }

    @Test
    fun loadSourceStandaloneSkipsTheAtlas() {
        val region = Textures.load("bg", VerktexTestFixtures.FakeSource(1920f, 1080f), standalone = true)!!
        assertEquals(1920f, region.texture.width)
        assertEquals(1080f, region.texture.height)
    }

    @Test
    fun loadSourceWithoutStandaloneUsesAnAtlas() {
        val region = Textures.load("small", VerktexTestFixtures.FakeSource(64f, 64f))!!
        assertNotEquals(64f, region.texture.width)
    }

    @Test
    fun loadSourceOverwriteReleasesPreviousStandalone() {
        val first = Textures.load("bg", VerktexTestFixtures.FakeSource(1920f, 1080f), standalone = true)!!
        Textures.load("bg", VerktexTestFixtures.FakeSource(1280f, 720f), standalone = true)
        VerktexTestFixtures.pumpDraw()
        assertTrue((first.texture as VerktexTestFixtures.FakeTexture).disposed)
    }

    @Test
    fun loadSourceStandaloneAboveMaxSizeReturnsNull() {
        assertNull(Textures.load("huge", VerktexTestFixtures.FakeSource(5000f, 5000f), standalone = true))
    }
}
