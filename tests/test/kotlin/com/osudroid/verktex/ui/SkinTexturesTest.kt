package com.osudroid.verktex.ui

import com.osudroid.ui.verktex.SkinTextures
import com.osudroid.verktex.VerktexTestFixtures
import com.reco1l.verktex.texture.Textures
import java.io.File
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SkinTexturesTest {
    @Test fun prefersSkinPngOverAsset() {
        val r = SkinTextures.resolve("logo", "logo.png", "/skin/") { it == "/skin/logo.png" }
        assertEquals(SkinTextures.Resolved("/skin/logo.png", isInternal = false), r)
    }

    @Test fun fallsBackToJpgThen2x() {
        assertEquals("/skin/menu-background.jpg", SkinTextures.resolve("menu-background", "gfx/menu-background.png", "/skin/") { it.endsWith(".jpg") }.path)
        assertEquals("/skin/star@2x.png", SkinTextures.resolve("star", "gfx/star.png", "/skin/") { it.endsWith("@2x.png") }.path)
    }

    @Test fun fallsBackToAsset() {
        assertEquals(SkinTextures.Resolved("gfx/star.png", isInternal = true), SkinTextures.resolve("star", "gfx/star.png", "/skin/") { false })
    }

    @Test fun skinPathWithAndWithoutTrailingSlashAgree() {
        val exists = { p: String -> p == "/skin/logo.png" }
        assertEquals(
            SkinTextures.resolve("logo", "logo.png", "/skin/", exists),
            SkinTextures.resolve("logo", "logo.png", "/skin", exists)
        )
        assertEquals("/skin/logo.png", SkinTextures.resolve("logo", "logo.png", "/skin", exists).path)
    }

    @After fun tearDown() {
        Textures.unloadAll()
    }

    @Test fun decodeThenRegisterStandaloneGivesSourceSizedTexture() {
        val provider = VerktexTestFixtures.ensureInitialized(maxTextureSize = 4096f)
        val file = File("bg-decode.png")
        provider.textureSizes[file.path] = 3000f to 1500f

        val source = SkinTextures.decode(file)
        assertNotNull(source)
        val region = SkinTextures.register("t-decode", source!!)!!

        assertEquals(3000f, region.texture.width)
    }

    @Test fun decodeReturnsNullForMissingFile() {
        VerktexTestFixtures.ensureInitialized(maxTextureSize = 4096f)
        assertEquals(null, SkinTextures.decode(File("missing.png")))
    }

    @Test fun registerOverwritesPreviousKey() {
        val provider = VerktexTestFixtures.ensureInitialized(maxTextureSize = 4096f)
        provider.textureSizes["first.png"] = 2500f to 1000f
        provider.textureSizes["second.png"] = 2600f to 1000f

        val first = SkinTextures.register("t-overwrite", SkinTextures.decode(File("first.png"))!!)!!
        val second = SkinTextures.register("t-overwrite", SkinTextures.decode(File("second.png"))!!)!!
        VerktexTestFixtures.pumpDraw()

        assertTrue((first.texture as VerktexTestFixtures.FakeTexture).disposed)
        assertFalse((second.texture as VerktexTestFixtures.FakeTexture).disposed)
    }
}
