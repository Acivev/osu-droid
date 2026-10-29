package com.osudroid.verktex.multi

import com.osudroid.multiplayer.api.data.PlayerStatus
import com.osudroid.multiplayer.api.data.RoomMods
import com.osudroid.multiplayer.api.data.RoomPlayer
import com.osudroid.ui.v2.multi.PlayerMessage
import com.osudroid.ui.v2.multi.RoomChat
import com.osudroid.verktex.VerktexTestFixtures
import com.osudroid.verktex.bridge.EngineRouter
import com.osudroid.verktex.hud.layoutOverWindow
import com.reco1l.verktex.BuiltIn
import com.reco1l.verktex.Verktex
import com.reco1l.verktex.data.px
import com.reco1l.verktex.fonts.Font
import com.reco1l.verktex.fonts.FontFamily
import com.reco1l.verktex.graphics.GraphicsTexture
import com.reco1l.verktex.ui.UIScene
import com.rian.andengine.modifier.ModifierRegistry
import com.rian.andengine.modifier.modifierTarget
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * RoomChat.kt before the port: an overlay that stays shown across scene changes, a ring buffer of the
 * last 50 messages, and an immediate collapse that drops the running height animation.
 */
@RunWith(RobolectricTestRunner::class)
class RoomChatTest {

    private val textures = listOf("chat.png", "send.png")
    private val previousTextureSizes = mutableMapOf<String, Pair<Float, Float>?>()

    private lateinit var chat: RoomChat

    @Before fun setUp() {
        LobbyTestSupport.setUp()

        val provider = VerktexTestFixtures.ensureInitialized()
        textures.forEach {
            previousTextureSizes[it] = provider.textureSizes[it]
            provider.textureSizes[it] = 64f to 64f
        }

        EngineRouter.leaveVerktex()

        chat = Verktex.withTreeLock { RoomChat() }.apply {
            post = { block -> Verktex.withTreeLock(block) }
            isInGameplay = { true }
        }
    }

    @After fun tearDown() {
        chat.hide()
        EngineRouter.leaveVerktex()

        val provider = VerktexTestFixtures.ensureInitialized()
        previousTextureSizes.forEach { (path, size) ->
            if (size == null) provider.textureSizes.remove(path) else provider.textureSizes[path] = size
        }

        LobbyTestSupport.tearDown()
    }

    private fun player(id: Long) = RoomPlayer(id, "player$id", 0, PlayerStatus.NotReady, null, RoomMods())


    @Test fun chatOverlaySurvivesSceneChange() {
        chat.show()
        assertTrue(chat.isShown)

        EngineRouter.showVerktex(UIScene())
        EngineRouter.leaveVerktex()

        assertTrue(chat.isShown)
        assertFalse(EngineRouter.isVerktexActive)

        chat.hide()
        assertFalse(chat.isShown)
    }

    @Test fun ringBufferKeepsTheLastMessages() {
        repeat(50 + 3) { chat.onRoomChatMessage(player(1), "message $it") }

        assertEquals(50, chat.messages.size)
        assertEquals("message 3", chat.messages.first.content)
        assertEquals("message 52", chat.messages.last.content)
        assertTrue(chat.messages.all { it is PlayerMessage })
    }

    @Test fun collapseImmediateSetsHeightZero() = Verktex.withTreeLock {
        chat.expand()
        assertTrue(chat.isExpanded)

        // Halfway through the 0.4 s expansion.
        ModifierRegistry.tick(0.2f)
        assertTrue(chat.body.modifierTarget.height > 0f)

        chat.collapse(true)

        assertFalse(chat.isExpanded)
        assertEquals(0f.px, chat.body.height)

        // No height modifier is left to move it again.
        ModifierRegistry.tick(0.2f)
        assertEquals(0f.px, chat.body.height)
        assertTrue(chat.body.modifierTarget.isEmpty)
    }

    @Test fun imeLiftPutsTheInputOnTheKeyboard() {
        // The AndEngine engine: max(0, inputBottom - (surfaceHeight - keyboardHeight)).
        assertEquals(320f, RoomChat.imeLift(inputBottom = 1000f, windowHeight = 1080f, keyboardHeight = 400f))
        assertEquals(0f, RoomChat.imeLift(inputBottom = 500f, windowHeight = 1080f, keyboardHeight = 400f))
        assertEquals(0f, RoomChat.imeLift(inputBottom = 1000f, windowHeight = 1080f, keyboardHeight = 0f))
    }

    @Test fun measuresWhileCollapsed() = Verktex.withTreeLock {
        // UIText takes the default family when it is built, glyphs are needed to measure.
        val previousFont = BuiltIn.Fonts.Default
        BuiltIn.Fonts.Default = FontFamily("chat-test", object : FontFamily.Source {
            override fun onCreateFont(descriptor: Font.Descriptor): Font = GlyphFont(descriptor)
        })

        try {
            val measuredChat = RoomChat()

            // The collapsed body is 0 high and its content keeps its size below it, as on AndEngine.
            layoutOverWindow(measuredChat)
            assertEquals(0f, measuredChat.body.measuredSize.y)
            assertTrue(measuredChat.buttonHeight > 0f)
        } finally {
            BuiltIn.Fonts.Default = previousFont
        }
    }

    /** Every glyph is a 10 x 10 square. */
    private class GlyphFont(override val descriptor: Descriptor) : Font() {
        override val metrics = Metrics(-10f, 4f, 0f)
        override val isKerningSupported = false

        override fun onCreatePage(): GraphicsTexture = VerktexTestFixtures.FakeTexture(256f, 256f)

        override fun onCreateGlyph(codePoint: Int) = Glyph(
            codePoint = codePoint,
            advance = 10f,
            width = 10f,
            height = 10f,
            region = addGlyphSource(VerktexTestFixtures.FakeSource(10f, 10f)),
        )

        override fun getKerning(first: Int, second: Int) = 0f
    }
}
