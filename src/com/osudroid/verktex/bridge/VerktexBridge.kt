package com.osudroid.verktex.bridge

import android.content.Context
import android.opengl.GLES32
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import com.reco1l.verktex.BuiltIn
import com.reco1l.verktex.GameHost
import com.reco1l.verktex.Logger
import com.reco1l.verktex.Platform
import com.reco1l.verktex.Verktex
import com.reco1l.verktex.android.AndroidLogger
import com.reco1l.verktex.android.gles.ESDevice
import com.reco1l.verktex.android.toInputKey
import com.reco1l.verktex.fonts.Fonts
import com.reco1l.verktex.input.Input
import com.reco1l.verktex.input.KeyboardEvent
import com.reco1l.verktex.input.PointerEvent
import org.andengine.engine.Engine
import org.andengine.input.touch.TouchEvent
import org.andengine.opengl.util.GLState
import ru.nsu.ccfit.zuev.osuplus.BuildConfig

/**
 * Runs Verktex inside AndEngine: AndEngine owns the EGL context, the threads and the input, and
 * pumps Verktex's workers from its own frame. Removed together with AndEngine.
 */
object VerktexBridge {

    private const val DRAW_WORKER = "draw_worker"
    private const val UPDATE_WORKER = "update_worker"

    @JvmStatic
    var isInitialized = false
        private set

    lateinit var platform: HostedPlatform
    lateinit var window: HostedWindow

    // The first surface is created after initialize(), every later one is a recreated context.
    private var surfaceGeneration = 0


    @JvmStatic
    fun initialize(host: GameHost, context: Context) {
        platform = HostedPlatform(context)
        window = HostedWindow()

        Platform.implementation = platform
        Logger.implementation = AndroidLogger()

        Verktex.initialize(
            host = host,
            renderer = HostedRenderer(
                context = HostedContext(),
                device = ESDevice(),
                window = window
            ),
            audio = NoopAudioDriver()
        )

        BuiltIn.Fonts.Default = Fonts.load("nunito", "fonts/nunito-medium.ttf", isInternal = true)!!
        Fonts.load("torus", "fonts/torus-bold.otf", isInternal = true)
        BuiltIn.Fonts.FontAwesomeIconRegular = Fonts.load("fa-regular", "fonts/fa-regular.otf", isInternal = true)!!
        BuiltIn.Fonts.FontAwesomeIconSolid = Fonts.load("fa-solid", "fonts/fa-solid.otf", isInternal = true)!!
        BuiltIn.Fonts.FontAwesomeIconBrands = Fonts.load("fa-brands", "fonts/fa-brands.otf", isInternal = true)!!
        host.onEngineInitialized()

        Verktex.start()
        isInitialized = true
        host.onEngineStarted()
    }

    /**
     * Must be called after `RenderSurfaceView.setRenderer`, which installs the engine as the touch
     * listener. This one takes its place and forwards touches to the engine, which queues them for
     * the update thread, except mouse wheel scrolls, which the engine does not queue.
     */
    @JvmStatic
    fun attachSurfaceView(view: View, engine: Engine) {
        window.attachInsetsListener(view)

        view.setOnTouchListener { touchedView, event ->
            if (event.action == MotionEvent.ACTION_SCROLL) {
                dispatchScroll(event)
            } else {
                engine.onTouch(touchedView, event)
            }
        }
    }

    @JvmStatic
    fun onSurfaceCreated() {
        surfaceGeneration++

        if (surfaceGeneration > 1) {
            Verktex.graphics.markContextLoss()
        }
    }

    @JvmStatic
    fun onSurfaceChanged(width: Int, height: Int) {
        window.windowWidth = width
        window.windowHeight = height
    }

    /**
     * Draws Verktex on AndEngine's GL thread, between the AndEngine scene and its HUD.
     */
    @JvmStatic
    fun pumpDraw(glState: GLState) {
        if (!isInitialized || window.windowWidth == 0 || window.windowHeight == 0) {
            return
        }

        platform.worker(DRAW_WORKER).pump()
        glState.resyncAfterExternalDraw()
        // The window holds the surface size from onSurfaceChanged, which AndEngine's viewport uses.
        GLES32.glViewport(0, 0, window.windowWidth, window.windowHeight)
    }

    /**
     * Updates Verktex on AndEngine's update thread.
     */
    @JvmStatic
    fun pumpUpdate() {
        if (!isInitialized) {
            return
        }

        platform.worker(UPDATE_WORKER).pump()
    }

    /**
     * True when the current thread is the one that last pumped the update worker.
     */
    @JvmStatic
    val isUpdateThread: Boolean
        get() = isInitialized && platform.workers[UPDATE_WORKER]?.pumpingThread == Thread.currentThread()

    @JvmStatic
    fun checkUpdateThread() {
        if (BuildConfig.DEBUG && isInitialized && !isUpdateThread) {
            throw IllegalStateException("not on the update thread")
        }
    }

    /**
     * Dispatches an AndEngine touch to Verktex, [action] being one of `TouchEvent.ACTION_*` and
     * [x], [y] being surface coordinates.
     */
    @JvmStatic
    fun dispatchPointer(action: Int, pointerId: Int, x: Float, y: Float, buttonState: Int): Boolean {
        if (!isInitialized) {
            return false
        }

        val pointerAction = when (action) {
            TouchEvent.ACTION_DOWN -> PointerEvent.Action.Down
            TouchEvent.ACTION_UP -> PointerEvent.Action.Up
            TouchEvent.ACTION_MOVE -> PointerEvent.Action.Move
            else -> PointerEvent.Action.Unknown
        }

        val button = when (buttonState) {
            MotionEvent.BUTTON_PRIMARY -> PointerEvent.Button.Left
            MotionEvent.BUTTON_SECONDARY -> PointerEvent.Button.Right
            else -> PointerEvent.Button.Unknown
        }

        return Input.dispatchPointerEvent(pointerAction, button, pointerId, x, y)
    }

    @JvmStatic
    fun dispatchScroll(event: MotionEvent): Boolean {
        return Input.dispatchScrollEvent(
            pointerId = event.getPointerId(event.actionIndex),
            deltaX = event.getAxisValue(MotionEvent.AXIS_HSCROLL),
            deltaY = event.getAxisValue(MotionEvent.AXIS_VSCROLL)
        )
    }

    @JvmStatic
    fun dispatchKey(event: KeyEvent): Boolean {
        val action = when (event.action) {
            KeyEvent.ACTION_DOWN -> KeyboardEvent.Action.Down
            KeyEvent.ACTION_UP -> KeyboardEvent.Action.Up
            else -> return false
        }

        return Input.dispatchKeyboardEvent(
            action = action,
            key = event.toInputKey(),
            shiftPressed = event.isShiftPressed,
            ctrlPressed = event.isCtrlPressed,
            altPressed = event.isAltPressed,
            metaPressed = event.isMetaPressed
        )
    }

    @JvmStatic
    fun onWindowFocusChanged(hasFocus: Boolean) = Verktex.onGameWindowFocusChange(hasFocus)

    @JvmStatic
    fun pause() = Verktex.pause()

    @JvmStatic
    fun resume() = Verktex.resume()

    @JvmStatic
    fun dispose() {
        isInitialized = false
        surfaceGeneration = 0
        Verktex.dispose()
    }
}
