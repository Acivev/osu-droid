package com.osudroid.resources

import android.content.Context
import com.osudroid.storyboard.renderer.StoryboardTexturePool
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.GraphicsMode
import ru.nsu.ccfit.zuev.osu.ResourceManager

/**
 * ResourceManager answers its texture lookups from the catalog with the truth table the AndEngine
 * stores had: Absent is loaded but null, an unknown name lazy-loads its default asset, and a
 * background that does not decode falls back to the menu background.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ResourceManagerOracleTest {

    @get:Rule
    val temporaryFolder = TemporaryFolder()

    private val resources = ResourceManager.getInstance()

    private lateinit var textureStore: TextureStore

    // ResourceManager.Init also sets up the sound stores and reads the app signature; the texture side only needs the context.
    private fun field(name: String) = ResourceManager::class.java.getDeclaredField(name).also { it.isAccessible = true }

    @Before
    fun setUp() {
        val context = RuntimeEnvironment.getApplication()

        field("context").set(resources, context)
        textureStore = field("textureStore").get(resources) as TextureStore

        for (name in arrayOf("textureStore", "customTextureStore")) {
            (field(name).get(resources) as TextureStore).also {
                it.init(context as Context)
                it.clear()
            }
        }
    }

    @Test
    fun absentLightingAnswersNull() {
        textureStore.markAbsent("lighting")

        assertNull(resources.getTexture("lighting"))
        assertTrue(resources.isTextureLoaded("lighting"))
    }

    @Test
    fun unloadingAbsentKeepsItAbsent() {
        textureStore.markAbsent("lighting")

        resources.unloadTexture("lighting")

        assertTrue(resources.isTextureLoaded("lighting"))
        assertNull(resources.getTextureIfLoaded("lighting"))
    }

    @Test
    fun unknownNameLazyLoadsTheAsset() {
        assertFalse(resources.isTextureLoaded("menu-background"))

        val entry = resources.getTexture("menu-background")

        assertNotNull(entry)
        assertEquals(TextureEntry.Source.DefaultAsset, entry.source)
        assertTrue(resources.isTextureLoaded("menu-background"))
    }

    @Test
    fun backgroundFallsBackToMenuBackground() {
        val menuBackground = resources.getTexture("menu-background")
        val file = temporaryFolder.newFile("bg.jpg").also { it.writeBytes(ByteArray(10)) }

        val entry = resources.loadBackground(file.path)

        assertNotNull(menuBackground)
        assertSame(menuBackground, entry)
        assertSame(menuBackground, resources.getTextureIfLoaded("::background"))
        assertEquals(file.path, resources.backgroundFile)
    }

    @Test
    fun storyboardSkinHitSetIgnoresAbsent() {
        val pool = StoryboardTexturePool(temporaryFolder.newFolder("beatmap"), true)
        val isSkinTexture = StoryboardTexturePool::class.java.getDeclaredMethod("isSkinTexture", String::class.java)
            .also { it.isAccessible = true }

        textureStore.markAbsent("lighting")
        assertNotNull(textureStore.load("hitcircle", "gfx/hitcircle.png", false))

        assertFalse(isSkinTexture.invoke(pool, "lighting.png") as Boolean)
        assertTrue(isSkinTexture.invoke(pool, "hitcircle.png") as Boolean)
    }
}
