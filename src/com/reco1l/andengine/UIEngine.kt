package com.reco1l.andengine

import android.app.Activity
import android.view.*
import com.osudroid.ui.verktex.ISkinnable as VerktexSkinnable
import com.osudroid.ui.verktex.SkinTextures
import com.osudroid.verktex.bridge.EngineRouter
import com.osudroid.verktex.bridge.InputRouter
import com.osudroid.verktex.bridge.VerktexBridge
import com.reco1l.verktex.Verktex
import com.rian.andengine.timing.IClockProvider
import com.rian.andengine.timing.ThrottledFrameClock
import org.andengine.engine.Engine
import org.andengine.engine.camera.hud.HUD
import org.andengine.engine.options.EngineOptions
import org.andengine.entity.scene.*
import org.andengine.input.touch.*
import org.andengine.opengl.util.GLState
import org.andengine.engine.camera.Camera

class UIEngine(val context: Activity, options: EngineOptions) : Engine(options),
    IClockProvider<ThrottledFrameClock> {
    override val clock = ThrottledFrameClock()

    /**
     * The global HUD used for overlays (menus, dialogs, etc).
     */
    val overlay = HUD()

    init {
        current = this
        camera.hud = overlay
    }


    override fun onDrawScene(pGLState: GLState, pCamera: Camera) {
        // Verktex draws above the AndEngine scene and below its HUD.
        scene?.onDraw(pGLState, pCamera)
        VerktexBridge.pumpDraw(pGLState)
        pCamera.onDrawHUD(pGLState)
    }


    /**
     * Called when the skin is changed.
     */
    fun onSkinChange() {
        SkinTextures.invalidate()

        // Runs after the invalidation, both are tasks of the Verktex update worker.
        if (VerktexBridge.isInitialized) {
            Verktex.scheduleOnUpdate {
                Verktex.scene.propagate { (it as? VerktexSkinnable)?.onSkinChanged() }
            }
        }
    }

    /**
     * Called when a key is pressed. The AndEngine tree holds no key handlers since the menus moved
     * to Verktex.
     */
    @Suppress("UNUSED_PARAMETER")
    fun onKeyPress(keyCode: Int, event: KeyEvent): Boolean = false


    /**
     * Runs on the update thread from `MultiTouchController.onUpdate`. Offers the touch to the HUD,
     * then Verktex, then the scene.
     */
    override fun onTouchEvent(event: TouchEvent): Boolean {
        val scene = getSceneFromSurfaceTouchEvent(event)
        val camera = getCameraFromSurfaceTouchEvent(event)

        // Verktex takes surface coordinates, the conversion below rewrites them in place.
        val surfaceX = event.x
        val surfaceY = event.y

        convertSurfaceToSceneTouchEvent(camera, event)

        return InputRouter.routeTouch(
            hud = { onTouchHUD(camera, event) },
            verktex = { VerktexBridge.dispatchPointer(
                event.action, event.pointerID, surfaceX, surfaceY,
                event.motionEvent?.buttonState ?: 0, event.motionEvent?.eventTime ?: 0L
            ) },
            scene = { onTouchScene(scene, event) }
        )
    }

    override fun onUpdate(pNanosecondsElapsed: Long) {
        clock.processFrame()

        super.onUpdate((clock.elapsedFrameTime * 1e9).toLong())
        VerktexBridge.pumpUpdate()
    }

    override fun setScene(scene: Scene?) {
        mScene?.onDetached()
        if (scene !== EngineRouter.emptyAndEngineScene) {
            EngineRouter.leaveVerktex()
        }
        super.setScene(scene)
        scene?.onAttached()
    }

    companion object {

        @JvmStatic
        lateinit var current: UIEngine
            private set

    }
}