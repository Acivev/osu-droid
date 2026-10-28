package com.osudroid.verktex.ui

import android.graphics.Bitmap
import com.osudroid.ui.verktex.SkinTextures
import com.osudroid.verktex.VerktexTestFixtures
import com.reco1l.verktex.texture.Textures
import java.io.File
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import ru.nsu.ccfit.zuev.osu.Config

// Robolectric decodes the PNG written by hdFilesDecodeAtHalfSize.
@RunWith(RobolectricTestRunner::class)
class SkinTexturesTest {
    @get:Rule val temporaryFolder = TemporaryFolder()

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

    @Test fun resolveReportsTheHdCandidate() {
        assertEquals(
            SkinTextures.Resolved("/skin/star@2x.png", isInternal = false, isHD = true),
            SkinTextures.resolve("star", "gfx/star.png", "/skin/") { it == "/skin/star@2x.png" }
        )
        assertFalse(SkinTextures.resolve("star", "gfx/star.png", "/skin/") { it == "/skin/star.png" }.isHD)
        assertFalse(SkinTextures.resolve("star", "gfx/star.png", "/skin/") { false }.isHD)
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

    @Test fun hdFilesDecodeAtHalfSize() {
        VerktexTestFixtures.ensureInitialized(maxTextureSize = 4096f)
        val file = temporaryFolder.newFile("x@2x.png")
        file.outputStream().use { Bitmap.createBitmap(64, 64, Bitmap.Config.ARGB_8888).compress(Bitmap.CompressFormat.PNG, 100, it) }

        val source = SkinTextures.decode(file, inSampleSize = 2)!!

        assertEquals(32f, source.width)
        assertEquals(32f, source.height)
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

    @Test fun beatmapSkinIsResolvedOnlyWhileEnabled() {
        val beatmapFile = File("/songs/1 Map/score-0.png")
        val custom = { name: String -> if (name == "score-0") beatmapFile else null }

        assertSame(beatmapFile, SkinTextures.resolveBeatmapSkin("score-0", enabled = true, customFile = custom))
        assertNull(SkinTextures.resolveBeatmapSkin("score-0", enabled = false, customFile = custom))
        assertNull(SkinTextures.resolveBeatmapSkin("score-1", enabled = true, customFile = custom))
    }

    @Test fun beatmapFolderWinsWhileEnabledUserSkinOtherwise() {
        val provider = VerktexTestFixtures.ensureInitialized(maxTextureSize = 4096f)
        setSkinPath("/test-user-skin/")

        // The beatmap's file and the bundled asset (the user skin has none) differ in size.
        val beatmapFile = File("/songs/2 Map/scorebar-bg.png")
        provider.textureSizes[beatmapFile.path] = 30f to 10f
        provider.textureSizes["gfx/scorebar-bg.png"] = 60f to 20f
        val custom = { name: String -> if (name == "scorebar-bg") beatmapFile else null }

        val fromBeatmap = SkinTextures.getWithBeatmapSkin("scorebar-bg", "gfx/scorebar-bg.png", enabled = true, customFile = custom)
        assertEquals(30f, fromBeatmap!!.width)

        val fromUserSkin = SkinTextures.getWithBeatmapSkin("scorebar-bg", "gfx/scorebar-bg.png", enabled = false, customFile = custom)
        assertEquals(60f, fromUserSkin!!.width)

        // Another beatmap's file for the same name is its own texture, not the cached one.
        val otherFile = File("/songs/3 Map/scorebar-bg.png")
        provider.textureSizes[otherFile.path] = 40f to 10f
        val other = SkinTextures.getWithBeatmapSkin("scorebar-bg", "gfx/scorebar-bg.png", enabled = true) { otherFile }
        assertEquals(40f, other!!.width)
    }

    private fun setSkinPath(path: String) {
        Config.setSkinPath(path)
    }
}
