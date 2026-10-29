package com.osudroid.verktex.game

import android.content.SharedPreferences
import android.os.Looper
import android.preference.PreferenceManager
import androidx.annotation.OptIn
import androidx.media3.common.Player
import androidx.media3.common.VideoSize
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import com.osudroid.game.PlayfieldTransform
import com.osudroid.storyboard.renderer.StoryboardComponent
import com.osudroid.ui.verktex.LegacyUnits
import com.osudroid.verktex.VerktexTestFixtures
import com.osudroid.game.UIVideoSprite
import com.osudroid.game.VideoTexture
import com.reco1l.verktex.Platform
import com.reco1l.verktex.Verktex
import com.reco1l.verktex.android.gles.ESExternalTexture
import com.reco1l.verktex.data.Anchor
import com.reco1l.verktex.data.px
import com.reco1l.verktex.graphics.GraphicsTexture
import com.reco1l.verktex.data.LayoutConstraints
import com.reco1l.verktex.texture.Textures
import com.reco1l.verktex.ui.UIComponent
import com.reco1l.verktex.ui.UIScene
import com.reco1l.verktex.ui.UISprite
import com.reco1l.verktex.ui.container.UIContainer
import java.lang.reflect.Proxy
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import ru.nsu.ccfit.zuev.osu.Config
import ru.nsu.ccfit.zuev.osu.GlobalManager
import ru.nsu.ccfit.zuev.osu.MainActivity
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

    // What loadStoryboard leaves for applyBackground once the storyboard is loaded.
    private fun withStoryboard(): StoryboardComponent {
        val preferences = preferencesField.get(null) as SharedPreferences
        preferences.edit().putBoolean("enableStoryboard", true).commit()

        return StoryboardComponent().also { setField("storyboardComponent", it) }
    }

    @Test fun storyboardSitsInsideThePlayfield() {
        val storyboard = withStoryboard()

        applyBackground()
        // Applying again, as every load step does, attaches it once.
        applyBackground()

        val playfield = field("playfield") as UIContainer
        val children = Verktex.withTreeLock { playfield.toList() }

        // Before the border, as AndEngine's attachChild(storyboard, 0) put it before the border attached at index 0.
        assertSame(playfield, storyboard.parent)
        assertSame(storyboard, children[0])
        assertSame(field("sceneBorder"), children[1])
        assertEquals(1, children.count { it === storyboard })
    }

    @Test fun overlaySitsBetweenMgAndFg() {
        val storyboard = withStoryboard()

        applyBackground()
        applyBackground()

        val playfield = field("playfield") as UIContainer
        val expected = listOf(storyboard, field("sceneBorder"), field("bgScene"), field("mgScene"), storyboard.overlayComponent, field("fgScene"))

        assertEquals(expected, Verktex.withTreeLock { playfield.toList() })
    }

    @Test fun holderMeasuresThePictureUnbounded() {
        // A picture larger than both the virtual resolution and the window, as createBackgroundPicture sizes it.
        val region = Textures.load("layer-order-bg", VerktexTestFixtures.FakeSource(1920f, 1080f), standalone = true)!!
        val picture = UISprite(region).apply {
            width = 1920f.px
            height = 1080f.px
        }
        setField("beatmapBackground", picture)

        try {
            applyBackground()

            val holder = field("backgroundHolder") as UIContainer
            val entity = field("backgroundEntity") as UIContainer

            // The scene measures its background within the window.
            Verktex.withTreeLock { holder.measure(LayoutConstraints(0f, 1280f, 0f, 720f)) }

            assertEquals(1920f, entity.measuredSize.x, EPSILON)
            assertEquals(1080f, entity.measuredSize.y, EPSILON)
            assertEquals(1920f, picture.measuredSize.x, EPSILON)
            assertEquals(1080f, picture.measuredSize.y, EPSILON)
        } finally {
            Textures.unload("layer-order-bg")
            VerktexTestFixtures.pumpDraw()
        }
    }

    @OptIn(UnstableApi::class)
    @Test fun videoRampStaysOnTheSprite() {
        // A dim of alpha 1, so only the ramp could lower an alpha.
        val preferences = preferencesField.get(null) as SharedPreferences
        preferences.edit().putInt("bgbrightness", 0).commit()

        // The player is built with the application context.
        val previousActivity = GlobalManager.getInstance().mainActivity
        GlobalManager.getInstance().mainActivity = Robolectric.buildActivity(MainActivity::class.java).get()

        try {
            var listener: Player.Listener? = null
            val playerFactory: (android.content.Context) -> ExoPlayer = {
                Proxy.newProxyInstance(ExoPlayer::class.java.classLoader, arrayOf(ExoPlayer::class.java)) { proxy, method, args ->
                    when (method.name) {
                        "hashCode" -> System.identityHashCode(proxy)
                        "equals" -> proxy === args?.get(0)
                        "toString" -> "FakePlayer"
                        "addListener" -> {
                            listener = args!![0] as Player.Listener
                            null
                        }
                        else -> null
                    }
                } as ExoPlayer
            }
            val textureFactory: (Float, Float) -> GraphicsTexture = { width, height ->
                object : ESExternalTexture(width, height) {
                    override fun onCreateTexture() = Unit
                    override fun bindExternalTexture() = Unit
                    override fun onUnload() = Unit
                }
            }

            // What loadVideo leaves once the player reported its size: a sprite of alpha 0.
            val video = UIVideoSprite(VideoTexture("video.mp4", playerFactory, textureFactory))
            video.alpha = 0f
            video.setOnReady {}
            shadowOf(Looper.getMainLooper()).idle()
            listener!!.onVideoSizeChanged(VideoSize(1280, 720))
            runCatching { (Platform.implementation as VerktexTestFixtures.FakePlatform).workers.getValue("update_worker").pump() }
            shadowOf(Looper.getMainLooper()).idle()

            setField("video", video)
            setField("videoEnabled", true)

            applyBackground()

            // The first step of update's ramp.
            video.alpha = minOf(video.alpha + 0.03f, 1f)

            // Applying again, as every load step does, leaves the ramp where it is.
            applyBackground()

            val entity = field("backgroundEntity") as UIComponent
            val dim = field("dimRectangle") as UIComponent

            assertSame(entity, video.parent)
            assertEquals(0.03f, video.alpha, EPSILON)
            assertEquals(1f, dim.alpha, EPSILON)
            assertEquals(1f, entity.alpha, EPSILON)
            assertEquals(1f, (field("backgroundHolder") as UIComponent).alpha, EPSILON)

            video.release()
            runCatching { (Platform.implementation as VerktexTestFixtures.FakePlatform).workers.getValue("update_worker").pump() }
            shadowOf(Looper.getMainLooper()).idle()
            VerktexTestFixtures.pumpDraw()
        } finally {
            GlobalManager.getInstance().mainActivity = previousActivity
        }
    }

    private companion object {
        const val EPSILON = 1e-5f
    }
}
