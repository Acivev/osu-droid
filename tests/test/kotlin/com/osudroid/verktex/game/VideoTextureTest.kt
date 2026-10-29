package com.osudroid.verktex.game

import android.os.Looper
import androidx.annotation.OptIn
import androidx.media3.common.Player
import androidx.media3.common.VideoSize
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import com.osudroid.verktex.VerktexTestFixtures
import com.osudroid.game.UIVideoSprite
import com.osudroid.game.VideoTexture
import com.reco1l.verktex.Platform
import com.reco1l.verktex.android.gles.ESExternalTexture
import com.reco1l.verktex.data.Anchor
import com.reco1l.verktex.data.px
import com.reco1l.verktex.graphics.GraphicsResource
import com.reco1l.verktex.graphics.GraphicsTexture
import com.reco1l.verktex.ui.UIComponent
import com.reco1l.verktex.ui.container.UIContainer
import java.lang.reflect.Proxy
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import ru.nsu.ccfit.zuev.osu.GlobalManager
import ru.nsu.ccfit.zuev.osu.MainActivity

/**
 * The video's player and its external texture: the texture exists (and is loaded on the draw thread) before the
 * player reports a size, since a decoder may report none until it has a surface; the sprite takes the reported
 * size; the surface is handed to the player whenever it is (re)created; release goes detach, player, texture.
 */
@OptIn(UnstableApi::class)
@RunWith(RobolectricTestRunner::class)
class VideoTextureTest {

    private val calls = mutableListOf<String>()
    private val textures = mutableListOf<RecordingTexture>()
    private var listener: Player.Listener? = null
    private var previousActivity: MainActivity? = null

    private inner class RecordingTexture(width: Float, height: Float) : ESExternalTexture(width, height) {
        override fun onCreateTexture() = Unit
        override fun bindExternalTexture() = Unit
        override fun onUnload() = Unit

        override fun dispose() {
            calls += "dispose"
            super.dispose()
        }
    }

    private val playerFactory: (android.content.Context) -> ExoPlayer = {
        Proxy.newProxyInstance(ExoPlayer::class.java.classLoader, arrayOf(ExoPlayer::class.java)) { proxy, method, args ->
            when (method.name) {
                "hashCode" -> System.identityHashCode(proxy)
                "equals" -> proxy === args?.get(0)
                "toString" -> "FakePlayer"
                "addListener" -> {
                    listener = args!![0] as Player.Listener
                    null
                }
                // The fake texture creates no surface, so the attach and the detach both pass null: the order tells them
                // apart.
                "setVideoSurface" -> {
                    calls += "setVideoSurface"
                    null
                }
                "release" -> {
                    calls += "release"
                    null
                }
                else -> null
            }
        } as ExoPlayer
    }

    private val textureFactory: (Float, Float) -> GraphicsTexture = { width, height ->
        calls += "createExternalTexture(${width}x$height)"
        RecordingTexture(width, height).also { textures += it }
    }

    // Runs what was scheduled with Verktex.scheduleOnUpdate. The update loop that follows the tasks needs the
    // built-in font, which the fakes do not load, so its failure is ignored: only the tasks matter here.
    private fun pumpUpdate() {
        runCatching { (Platform.implementation as VerktexTestFixtures.FakePlatform).workers.getValue("update_worker").pump() }
    }

    private fun idleMain() = shadowOf(Looper.getMainLooper()).idle()

    @Before fun setUp() {
        VerktexTestFixtures.ensureInitialized()

        // The player is built with the application context.
        previousActivity = GlobalManager.getInstance().mainActivity
        GlobalManager.getInstance().mainActivity = Robolectric.buildActivity(MainActivity::class.java).get()
    }

    @After fun tearDown() {
        GlobalManager.getInstance().mainActivity = previousActivity
    }

    @Test
    fun textureIsCreatedAndLoadedBeforeTheSizeIsKnown() {
        val video = VideoTexture("video.mp4", playerFactory, textureFactory)

        assertEquals(listOf("createExternalTexture(16.0x16.0)"), calls)
        assertSame(textures.single(), video.texture)
        assertEquals(GraphicsResource.State.Unloaded, video.texture.state)

        VerktexTestFixtures.pumpDraw()

        assertEquals(GraphicsResource.State.Loaded, video.texture.state)
        assertEquals(0, video.videoWidth)
        assertEquals(0, video.videoHeight)
    }

    @Test
    fun sizeReadySizesTheSpriteOnce() {
        val sprite = UIVideoSprite(VideoTexture("video.mp4", playerFactory, textureFactory))
        var readyCount = 0
        sprite.setOnReady { readyCount++ }
        idleMain()

        sprite.scaleOrigin = Anchor.TopLeft
        listener!!.onVideoSizeChanged(VideoSize(1280, 720))
        pumpUpdate()
        idleMain()

        assertEquals(1280f.px, sprite.width)
        assertEquals(720f.px, sprite.height)
        assertEquals(1280f, sprite.videoWidth)
        assertEquals(720f, sprite.videoHeight)
        assertSame(Anchor.Center, sprite.scaleOrigin)
        assertSame(textures.single().region, sprite.textureRegion)
        assertFalse(sprite.flippedVertical)
        assertEquals(1, readyCount)

        listener!!.onVideoSizeChanged(VideoSize(1920, 1080))
        pumpUpdate()
        idleMain()

        assertEquals(1, readyCount)
    }

    @Test
    fun surfaceRecreatedAttachesThePlayer() {
        val video = VideoTexture("video.mp4", playerFactory, textureFactory)
        idleMain()
        assertFalse("setVideoSurface" in calls)

        VerktexTestFixtures.pumpDraw()
        idleMain()

        assertEquals(1, calls.count { it == "setVideoSurface" })

        // A context loss recreates the surface on the next bind, the player is attached again.
        video.texture.markContextLost()
        video.texture.bind()
        idleMain()

        assertEquals(2, calls.count { it == "setVideoSurface" })
    }

    @Test
    fun releaseDetachesThenStopsThePlayerThenDisposes() {
        val sprite = UIVideoSprite(VideoTexture("video.mp4", playerFactory, textureFactory))
        val parent = object : UIContainer() {
            override fun onChildDetached(child: UIComponent) {
                calls += "detach"
            }
        }
        parent += sprite
        VerktexTestFixtures.pumpDraw()
        idleMain()
        calls.clear()

        sprite.release()
        pumpUpdate()
        idleMain()
        VerktexTestFixtures.pumpDraw()

        assertNull(sprite.parent)
        assertEquals(listOf("detach", "setVideoSurface", "release", "dispose"), calls)

        // A surface recreated after the release is not handed to the released player.
        textures.single().markContextLost()
        textures.single().bind()
        idleMain()
        assertEquals(1, calls.count { it == "setVideoSurface" })

        // Releasing twice (finalize after an explicit release) does nothing more.
        sprite.release()
        pumpUpdate()
        idleMain()
        VerktexTestFixtures.pumpDraw()
        assertEquals(4, calls.size)
    }
}
