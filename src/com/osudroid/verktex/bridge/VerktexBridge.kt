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
import com.reco1l.verktex.time.SystemClock
import com.rian.andengine.modifier.ModifierRegistry
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

    // SystemClock time of the last modifier tick, NaN before the first one.
    private var lastModifierTickTime = Float.NaN


    @JvmStatic
    fun initialize(host: GameHost, context: Context, updateThread: Thread) {
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
        // Runnables posted to the update thread can run before the update worker is first pumped.
        platform.worker(UPDATE_WORKER).bindThread(updateThread)
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
     * Updates Verktex on AndEngine's update thread. The modifiers are ticked first, so the update
     * and the next draw see their values.
     *
     * Their delta is measured here from [SystemClock], the time source of [Verktex.clock], because
     * that clock only advances inside the pump (after the update worker's throttle sleep). The sum
     * matches the clock's, each frame may be off by that sleep.
     *
     * While AndEngine is stopped (the activity is in the background) this is not called, so the
     * first tick after resuming covers the whole background time. It is left uncapped on purpose, as
     * on the reco1l path. Only [Verktex.pause] is skipped, its clock does not run then.
     */
    @JvmStatic
    fun pumpUpdate() {
        if (!isInitialized) {
            return
        }

        // Verktex's clock only ticks inside the pump, so the delta is taken from its time source
        // (SystemClock) here. Its pauses are skipped the same way, the clock does not run then.
        val now = SystemClock.time
        if (Verktex.clock.isRunning && !lastModifierTickTime.isNaN()) {
            val delta = now - lastModifierTickTime
            Verktex.withTreeLock { ModifierRegistry.tick(delta) }
        }
        lastModifierTickTime = now

        platform.worker(UPDATE_WORKER).pump()
    }

    /**
     * True when the current thread is AndEngine's update thread, which pumps the update worker.
     */
    @JvmStatic
    val isUpdateThread: Boolean
        get() = isInitialized && platform.workers[UPDATE_WORKER]?.isCurrentThread == true

    @JvmStatic
    fun checkUpdateThread() {
        if (BuildConfig.DEBUG && isInitialized && !isUpdateThread) {
            throw IllegalStateException("not on the update thread")
        }
    }

    /**
     * Dispatches an AndEngine touch to Verktex, [action] being one of `TouchEvent.ACTION_*` and
     * [x], [y] being surface coordinates and [eventTime] the `MotionEvent` uptime in milliseconds.
     */
    @JvmStatic
    fun dispatchPointer(action: Int, pointerId: Int, x: Float, y: Float, buttonState: Int, eventTime: Long): Boolean {
        if (!isInitialized) {
            return false
        }

        val pointerAction = when (action) {
            TouchEvent.ACTION_DOWN -> PointerEvent.Action.Down
            TouchEvent.ACTION_UP -> PointerEvent.Action.Up
            TouchEvent.ACTION_MOVE -> PointerEvent.Action.Move
            TouchEvent.ACTION_CANCEL -> PointerEvent.Action.Cancel
            else -> PointerEvent.Action.Unknown
        }

        val button = when (buttonState) {
            MotionEvent.BUTTON_PRIMARY -> PointerEvent.Button.Left
            MotionEvent.BUTTON_SECONDARY -> PointerEvent.Button.Right
            else -> PointerEvent.Button.Unknown
        }

        return Input.dispatchPointerEvent(pointerAction, button, pointerId, x, y, time = eventTime)
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
