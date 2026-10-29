package com.osudroid.verktex.bridge

import android.view.MotionEvent
import com.osudroid.verktex.VerktexTestFixtures
import com.reco1l.verktex.input.InputEvent
import com.reco1l.verktex.input.PointerEvent
import com.reco1l.verktex.ui.UIScene
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class VerktexBridgeDispatchTest {

    // VerktexBridge.initialize needs Android; the fixtures initialize Verktex itself, so only the flag is set.
    private val initializedField = VerktexBridge::class.java.getDeclaredField("isInitialized").apply {
        isAccessible = true
    }

    private class RecordingScene : UIScene() {
        var action: PointerEvent.Action? = null
        var button: PointerEvent.Button? = null
        var time: Long? = null

        override fun onInputEvent(event: InputEvent): Boolean {
            if (event is PointerEvent) {
                action = event.action
                button = event.button
                time = event.time
            }
            return true
        }
    }

    private val scene = RecordingScene()

    @Before
    fun setUp() {
        VerktexTestFixtures.ensureInitialized()
        initializedField.setBoolean(null, true)
        EngineRouter.showVerktex(scene)
    }

    @After
    fun tearDown() {
        EngineRouter.leaveVerktex()
        initializedField.setBoolean(null, false)
    }

    private fun dispatch(action: Int, buttonState: Int = MotionEvent.BUTTON_PRIMARY, eventTime: Long = 0L) {
        assertTrue(VerktexBridge.dispatchPointer(action, 0, 10f, 10f, buttonState, eventTime))
    }

    @Test
    fun mapsActions() {
        dispatch(MotionEvent.ACTION_DOWN)
        assertEquals(PointerEvent.Action.Down, scene.action)
        dispatch(MotionEvent.ACTION_MOVE)
        assertEquals(PointerEvent.Action.Move, scene.action)
        dispatch(MotionEvent.ACTION_UP)
        assertEquals(PointerEvent.Action.Up, scene.action)
        dispatch(MotionEvent.ACTION_OUTSIDE)
        assertEquals(PointerEvent.Action.Unknown, scene.action)
    }

    @Test
    fun cancelIsDispatchedAsCancel() {
        dispatch(MotionEvent.ACTION_DOWN)
        dispatch(MotionEvent.ACTION_CANCEL)
        assertEquals(PointerEvent.Action.Cancel, scene.action)
    }

    @Test
    fun eventTimeIsForwarded() {
        dispatch(MotionEvent.ACTION_DOWN, eventTime = 1234L)
        assertEquals(1234L, scene.time)
        dispatch(MotionEvent.ACTION_MOVE, eventTime = 1250L)
        assertEquals(1250L, scene.time)
    }

    @Test
    fun mapsButtons() {
        dispatch(MotionEvent.ACTION_DOWN, MotionEvent.BUTTON_PRIMARY)
        assertEquals(PointerEvent.Button.Left, scene.button)
        dispatch(MotionEvent.ACTION_DOWN, MotionEvent.BUTTON_SECONDARY)
        assertEquals(PointerEvent.Button.Right, scene.button)
        dispatch(MotionEvent.ACTION_DOWN, MotionEvent.BUTTON_TERTIARY)
        assertEquals(PointerEvent.Button.Unknown, scene.button)
        dispatch(MotionEvent.ACTION_DOWN, 0)
        assertEquals(PointerEvent.Button.Unknown, scene.button)
    }
}
