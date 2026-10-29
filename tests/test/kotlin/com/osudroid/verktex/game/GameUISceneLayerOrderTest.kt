package com.osudroid.verktex.game

import android.content.SharedPreferences
import android.preference.PreferenceManager
import com.osudroid.game.PlayfieldTransform
import com.osudroid.ui.verktex.LegacyUnits
import com.osudroid.verktex.VerktexTestFixtures
import com.reco1l.verktex.Verktex
import com.reco1l.verktex.data.Anchor
import com.reco1l.verktex.ui.UIComponent
import com.reco1l.verktex.ui.UIScene
import com.reco1l.verktex.ui.container.UIContainer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import ru.nsu.ccfit.zuev.osu.Config
import ru.nsu.ccfit.zuev.osu.game.GameScene

/**
 * The gameplay background is the game scene's background, in the un-zoomed window space as AndEngine's
 * scene background was: the playfield size scales the playfield, not the background. The dim is a
 * sibling of the picture, drawn over it, and the playfield keeps the border behind its three layers.
 */
@RunWith(RobolectricTestRunner::class)
class GameUISceneLayerOrderTest {

    // Config reads the brightness and the border setting from its preferences, which only loadConfig sets.
    private val preferencesField = Config::class.java.getDeclaredField("sharedPreferences").apply { isAccessible = true }
    private var previousPreferences: SharedPreferences? = null
    private var previousResWidth = 0
    private var previousResHeight = 0
    private var previousPlayfieldSize = 1f

    private lateinit var game: GameScene

    private fun field(name: String): Any? =
        GameScene::class.java.getDeclaredField(name).apply { isAccessible = true }.get(game)

    private fun setField(name: String, value: Any?) =
        GameScene::class.java.getDeclaredField(name).apply { isAccessible = true }.set(game, value)

    // The update thread runs it after every load step.
    private fun applyBackground() {
        GameScene::class.java.getDeclaredMethod("applyBackground").apply { isAccessible = true }.invoke(game)
    }

    @Before fun setUp() {
        VerktexTestFixtures.ensureInitialized()

        previousPreferences = preferencesField.get(null) as SharedPreferences?
        val preferences = PreferenceManager.getDefaultSharedPreferences(RuntimeEnvironment.getApplication())
        preferences.edit().putBoolean("displayPlayfieldBorder", true).commit()
        preferencesField.set(null, preferences)

        // The fake window is 1280x720, so the virtual resolution is drawn at twice its size.
        previousResWidth = Config.getRES_WIDTH()
        previousResHeight = Config.getRES_HEIGHT()
        Config.setRES_WIDTH(640)
        Config.setRES_HEIGHT(360)
        previousPlayfieldSize = Config.getPlayfieldSize()
        Config.setPlayfieldSize(0.8f)

        game = GameScene(null)

        // The playfield transform startGame applies.
        val transform = field("playfieldTransform") as FloatArray
        PlayfieldTransform.compute(Config.getPlayfieldSize(), 0.5f, 0.5f, 640f, 360f, 1280f, 720f, LegacyUnits.scale, transform)
        val playfield = field("playfield") as UIContainer
        playfield.scaleOrigin = Anchor.TopLeft
        playfield.scale = transform[0]

        // What loadBackground records for a beatmap whose background slot was never filled.
        setField("backgroundSource", GameScene.BackgroundSource.Colour)
    }

    @After fun tearDown() {
        Config.setPlayfieldSize(previousPlayfieldSize)
        Config.setRES_WIDTH(previousResWidth)
        Config.setRES_HEIGHT(previousResHeight)
        preferencesField.set(null, previousPreferences)
        previousPreferences = null
    }

    @Test fun holderIgnoresThePlayfieldScale() {
        applyBackground()

        val scene = field("scene") as UIScene
        val holder = field("backgroundHolder") as UIContainer
        val playfield = field("playfield") as UIContainer

        assertSame(holder, scene.background)
        assertNull(holder.parent)
        assertEquals(2f, LegacyUnits.scale, EPSILON)
        assertEquals(LegacyUnits.scale, holder.scaleX, EPSILON)
        assertEquals(LegacyUnits.scale, holder.scaleY, EPSILON)
        assertEquals(Anchor.TopLeft, holder.scaleOrigin)
        assertEquals(LegacyUnits.scale * 0.8f, playfield.scaleX, EPSILON)
        assertSame(holder, (field("backgroundEntity") as UIComponent).parent)
    }

    @Test fun dimIsASiblingOfThePicture() {
        applyBackground()
        // Applying again, as every load step does, rebuilds the same two children.
        applyBackground()

        val entity = field("backgroundEntity") as UIContainer
        val picture = field("beatmapBackground") as UIComponent
        val dim = field("dimRectangle") as UIComponent

        assertEquals(listOf(picture, dim), Verktex.withTreeLock { entity.toList() })
        assertEquals(Anchor.Center, entity.scaleOrigin)
    }

    @Test fun playfieldChildrenAreBorderBgMgFg() {
        applyBackground()

        val playfield = field("playfield") as UIContainer
        val expected = listOf(field("sceneBorder"), field("bgScene"), field("mgScene"), field("fgScene"))

        assertEquals(expected, Verktex.withTreeLock { playfield.toList() })
    }

    private companion object {
        const val EPSILON = 1e-5f
    }
}
