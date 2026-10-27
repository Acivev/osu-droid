package com.osudroid.verktex.bridge

import android.view.MotionEvent
import com.osudroid.verktex.VerktexTestFixtures
import com.reco1l.verktex.input.InputEvent
import com.reco1l.verktex.input.PointerEvent
import com.reco1l.verktex.ui.UIScene
import org.andengine.input.touch.TouchEvent
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class VerktexBridgeDispatchTest {
    private val defaultSceneSetter = EngineRouter.andEngineSceneSetter

    // VerktexBridge.initialize needs Android; the fixtures initialize Verktex itself, so only the flag is set.
    private val initializedField = VerktexBridge::class.java.getDeclaredField("isInitialized").apply {
        isAccessible = true
    }

    private class RecordingScene : UIScene() {
        var action: PointerEvent.Action? = null
        var button: PointerEvent.Button? = null

        override fun onInputEvent(event: InputEvent): Boolean {
            if (event is PointerEvent) {
                action = event.action
                button = event.button
            }
            return true
        }
    }

    private val scene = RecordingScene()

    @Before
    fun setUp() {
        VerktexTestFixtures.ensureInitialized()
        initializedField.setBoolean(null, true)
        EngineRouter.andEngineSceneSetter = {}
        EngineRouter.showVerktex(scene)
    }

    @After
    fun tearDown() {
        EngineRouter.leaveVerktex()
        EngineRouter.andEngineSceneSetter = defaultSceneSetter
        initializedField.setBoolean(null, false)
    }

    private fun dispatch(action: Int, buttonState: Int = MotionEvent.BUTTON_PRIMARY) {
        assertTrue(VerktexBridge.dispatchPointer(action, 0, 10f, 10f, buttonState))
    }

    @Test
    fun mapsActions() {
        dispatch(TouchEvent.ACTION_DOWN)
        assertEquals(PointerEvent.Action.Down, scene.action)
        dispatch(TouchEvent.ACTION_MOVE)
        assertEquals(PointerEvent.Action.Move, scene.action)
        dispatch(TouchEvent.ACTION_UP)
        assertEquals(PointerEvent.Action.Up, scene.action)
        dispatch(TouchEvent.ACTION_CANCEL)
        assertEquals(PointerEvent.Action.Unknown, scene.action)
        dispatch(TouchEvent.ACTION_OUTSIDE)
        assertEquals(PointerEvent.Action.Unknown, scene.action)
    }

    @Test
    fun mapsButtons() {
        dispatch(TouchEvent.ACTION_DOWN, MotionEvent.BUTTON_PRIMARY)
        assertEquals(PointerEvent.Button.Left, scene.button)
        dispatch(TouchEvent.ACTION_DOWN, MotionEvent.BUTTON_SECONDARY)
        assertEquals(PointerEvent.Button.Right, scene.button)
        dispatch(TouchEvent.ACTION_DOWN, MotionEvent.BUTTON_TERTIARY)
        assertEquals(PointerEvent.Button.Unknown, scene.button)
        dispatch(TouchEvent.ACTION_DOWN, 0)
        assertEquals(PointerEvent.Button.Unknown, scene.button)
    }
}
