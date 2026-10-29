package com.osudroid.verktex.hud

import com.osudroid.ui.v2.hud.GameplayHUD
import com.osudroid.ui.v2.hud.GameplayLayer
import com.osudroid.ui.verktex.PersistentOverlays
import com.osudroid.verktex.VerktexTestFixtures
import com.osudroid.verktex.bridge.EngineRouter
import com.reco1l.verktex.Verktex
import com.reco1l.verktex.data.fill
import com.reco1l.verktex.data.px
import com.reco1l.verktex.input.Input
import com.reco1l.verktex.input.InputEvent
import com.reco1l.verktex.input.PointerEvent
import com.reco1l.verktex.time.TickingClock
import com.reco1l.verktex.ui.UIComponent
import com.reco1l.verktex.ui.UIScene
import com.reco1l.verktex.ui.container.UIContainer
import com.reco1l.verktex.ui.shape.UIBox
import com.rian.andengine.modifier.ModifierRegistry
import com.rian.andengine.modifier.fadeTo
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import ru.nsu.ccfit.zuev.osu.Config

class GameplayLayerTest {

    private val overlays = mutableListOf<UIComponent>()

    private lateinit var layer: GameplayLayer
    private lateinit var hud: GameplayHUD

    @Before
    fun setUp() {
        VerktexTestFixtures.ensureInitialized()
        EngineRouter.leaveVerktex()

        Config.setRES_WIDTH(1280)
        Config.setRES_HEIGHT(720)

        layer = GameplayLayer()
        hud = GameplayHUD().apply { sceneCheck = { true } }
        layer.addToBack(hud)
    }

    private val defaultFpsRoot = PersistentOverlays.fpsRoot
    private val defaultChat = PersistentOverlays.chat

    @After
    fun tearDown() {
        PersistentOverlays.fpsRoot = defaultFpsRoot
        PersistentOverlays.chat = defaultChat
        layer.hide()
        overlays.forEach(Verktex::removeOverlay)
        EngineRouter.leaveVerktex()
    }

    private fun addOverlay(component: UIComponent) {
        Verktex.addOverlay(component)
        overlays += component
    }

    private fun press(): Boolean =
        Input.dispatchPointerEvent(PointerEvent.Action.Down, PointerEvent.Button.Left, 0, 100f, 100f)

    @Test
    fun gameplayLayerIsNotTracked() {
        layer.show()

        assertTrue(layer.isShown)
        assertFalse(EngineRouter.hasShownOverlay)
        assertFalse(EngineRouter.isVerktexActive)
        assertSame(UIScene.EmptyScene, Verktex.scene)
    }

    @Test
    fun showPutsFpsAndChatAboveTheLayer() {
        val log = mutableListOf<String>()

        // The FPS counter is an overlay from startup and the chat is shown in the room, both before the layer.
        val fps = PressRecorder("fps", log)
        val chat = PressRecorder("chat", log)
        addOverlay(fps)
        addOverlay(chat)

        hud += PressRecorder("hud", log)
        PersistentOverlays.fpsRoot = { fps }
        PersistentOverlays.chat = { chat }

        layer.show()
        listOf(layer, fps, chat).forEach(::layoutOverWindow)

        assertFalse(press())

        // Topmost overlays get the press first: chat, then the FPS counter, then the layer.
        assertEquals(listOf("chat", "fps", "hud"), log)
    }

    @Test
    fun showReordersFpsAndChatWithoutDetaching() {
        val log = mutableListOf<String>()
        val detached = mutableListOf<String>()

        // A detached chat collapses (RoomChat.onDetached), so the reorder must keep both attached.
        class DetachRecorder(val name: String) : UIContainer() {
            init {
                width = fill()
                height = fill()
            }

            override fun onInputEvent(event: InputEvent): Boolean {
                if (event is PointerEvent && event.action == PointerEvent.Action.Down) log += name
                return false
            }

            override fun onDetached() {
                detached += name
            }
        }

        val fps = DetachRecorder("fps")
        val chat = DetachRecorder("chat")
        // Added in the reverse order, so show() has to move both.
        addOverlay(chat)
        addOverlay(fps)

        PersistentOverlays.fpsRoot = { fps }
        PersistentOverlays.chat = { chat }
        layer.show()

        assertTrue(detached.isEmpty())
        assertNotNull(fps.parent)
        assertNotNull(chat.parent)

        listOf(layer, fps, chat).forEach(::layoutOverWindow)
        hud += PressRecorder("hud", log)
        layoutOverWindow(layer)
        assertFalse(press())
        assertEquals(listOf("chat", "fps", "hud"), log)
    }

    @Test
    fun topOverlayIsAboveEverything() {
        val log = mutableListOf<String>()
        val menu = PressRecorder("menu", log, consume = true)

        hud += PressRecorder("hud", log)

        layer.show()
        layer.showTopOverlay(menu)
        listOf(layer, menu).forEach(::layoutOverWindow)

        assertTrue(press())
        assertEquals(listOf("menu"), log)

        layer.hide()
        assertFalse(menu.parent != null)
    }

    @Test
    fun setHudPausedStopsUpdateAndModifiers() {
        var updates = 0

        val child = object : UIBox() {
            override fun onUpdate(clock: TickingClock) {
                updates++
                super.onUpdate(clock)
            }
        }
        child.width = 10f.px
        hud += child

        child.fadeTo(0f, 1f)

        layer.setHudPaused(hud, true)
        ModifierRegistry.tick(0.5f)
        hud.update(Verktex.clock)

        assertEquals(1f, child.alpha, 0f)
        assertEquals(0, updates)

        layer.setHudPaused(hud, false)
        ModifierRegistry.tick(0.5f)
        hud.update(Verktex.clock)

        assertTrue(child.alpha < 1f)
        assertEquals(1, updates)
    }

    @Test
    fun trackedOverlayShownLaterStaysBelowFpsAndChat() {
        val log = mutableListOf<String>()

        val fps = PressRecorder("fps", log)
        val chat = PressRecorder("chat", log)
        addOverlay(fps)
        addOverlay(chat)
        PersistentOverlays.fpsRoot = { fps }
        PersistentOverlays.chat = { chat }

        // The mod menu, the attribute display and the calibration scene are tracked router overlays.
        val modMenu = PressRecorder("modMenu", log)
        EngineRouter.showOverlay(modMenu)

        try {
            listOf(fps, chat, modMenu).forEach(::layoutOverWindow)
            assertFalse(press())
            assertEquals(listOf("chat", "fps", "modMenu"), log)
        } finally {
            EngineRouter.hideOverlay(modMenu)
        }
    }
}
