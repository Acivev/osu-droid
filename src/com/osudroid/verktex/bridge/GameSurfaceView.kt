package com.osudroid.verktex.bridge

import android.annotation.SuppressLint
import android.content.Context
import android.opengl.GLES32
import android.opengl.GLSurfaceView
import android.os.Build
import android.util.Log
import javax.microedition.khronos.egl.EGL10
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.egl.EGLContext
import javax.microedition.khronos.egl.EGLDisplay
import javax.microedition.khronos.opengles.GL10
import kotlin.math.roundToInt
import ru.nsu.ccfit.zuev.osu.Config

/**
 * The surface the game renders into, ported from AndEngine's `RenderSurfaceView`: an OpenGL ES 3.2 context (falling
 * back to 3.0), an RGBA8 config picked by [EglConfigLadder], a preserved EGL context across pause, and a measure that
 * letterboxes the view to the `Config` resolution ratio as AndEngine's `RatioResolutionPolicy` did.
 */
@SuppressLint("ViewConstructor")
class GameSurfaceView(context: Context) : GLSurfaceView(context) {

    fun setup(renderer: Renderer) {
        setEGLContextFactory(GLES32ContextFactory())
        setEGLConfigChooser(LadderConfigChooser())

        // Keep the EGL context alive while the app is paused so that GPU resources survive a normal
        // background/foreground transition. The system will still destroy the context under memory pressure.
        preserveEGLContextOnPause = true

        setRenderer(renderer)
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        if (isInEditMode) {
            super.onMeasure(widthMeasureSpec, heightMeasureSpec)
            return
        }

        if (MeasureSpec.getMode(widthMeasureSpec) != MeasureSpec.EXACTLY || MeasureSpec.getMode(heightMeasureSpec) != MeasureSpec.EXACTLY) {
            throw IllegalStateException("This IResolutionPolicy requires MeasureSpec.EXACTLY, which means both width and height must be measured with MeasureSpec.EXACTLY.")
        }

        val resWidth = Config.getRES_WIDTH()
        val resHeight = Config.getRES_HEIGHT()
        // Config is not loaded on the permission-stub path; the view then fills its parent.
        val ratio = if (resWidth == 0 || resHeight == 0) 0f else resWidth.toFloat() / resHeight

        val measured = RatioMeasure.measure(ratio, MeasureSpec.getSize(widthMeasureSpec), MeasureSpec.getSize(heightMeasureSpec))
        setMeasuredDimension(measured[0], measured[1])
    }

    /**
     * The renderer [setup] installs. [onSurfaceCreated] applies the GL defaults `EngineRenderer` applied, then calls
     * [onSurfaceReady]. [onDrawFrame] clears colour (opaque black), depth and stencil, then calls [onFrame].
     * [onSurfaceChanged] is left to the subclass.
     *
     * The clear lives here because the empty AndEngine scene's background and `EngineRenderer`'s per-frame depth clear
     * used to do it; without it a frame Verktex skips (paused or not yet sized) would show undefined buffer contents.
     */
    abstract class Renderer : GLSurfaceView.Renderer {

        final override fun onSurfaceCreated(gl: GL10?, config: EGLConfig?) {
            GLES32.glDisable(GLES32.GL_DEPTH_TEST)
            // Initial depth clear value 1.0 (maximum) so that GL_LESS tests pass on first draw.
            GLES32.glClearDepthf(1f)
            GLES32.glDepthFunc(GLES32.GL_LESS)
            GLES32.glDepthMask(true)
            GLES32.glEnable(GLES32.GL_BLEND)
            GLES32.glDisable(GLES32.GL_DITHER)

            onSurfaceReady()
        }

        final override fun onDrawFrame(gl: GL10?) {
            GLES32.glClearColor(0f, 0f, 0f, 1f)
            GLES32.glClear(GLES32.GL_COLOR_BUFFER_BIT or GLES32.GL_DEPTH_BUFFER_BIT or GLES32.GL_STENCIL_BUFFER_BIT)

            try {
                onFrame()
            } catch (e: InterruptedException) {
                Log.e(TAG, "GLThread interrupted!", e)
            }
        }

        /**
         * Called on the GL thread after the GL defaults are applied, for every new (or recreated) surface.
         */
        abstract fun onSurfaceReady()

        /**
         * Called on the GL thread every frame after the clear.
         */
        abstract fun onFrame()
    }

    /**
     * EGL context factory that requests an OpenGL ES 3.2 context and falls back to 3.0 on devices that do not support
     * the minor version attribute.
     */
    private class GLES32ContextFactory : EGLContextFactory {

        override fun createContext(egl: EGL10, display: EGLDisplay, eglConfig: EGLConfig): EGLContext {
            // Try OpenGL ES 3.2 first.
            val attribs32 = intArrayOf(
                EGL_CONTEXT_CLIENT_VERSION, 3,
                EGL_CONTEXT_MINOR_VERSION_KHR, 2,
                EGL10.EGL_NONE,
            )
            var context = egl.eglCreateContext(display, eglConfig, EGL10.EGL_NO_CONTEXT, attribs32)
            if (context != null && context != EGL10.EGL_NO_CONTEXT) {
                Log.i(TAG, "Created OpenGL ES 3.2 context.")
                return context
            }

            // Fall back to OpenGL ES 3.0.
            Log.w(TAG, "OpenGL ES 3.2 not available, falling back to ES 3.0.")
            val attribs30 = intArrayOf(
                EGL_CONTEXT_CLIENT_VERSION, 3,
                EGL10.EGL_NONE,
            )
            context = egl.eglCreateContext(display, eglConfig, EGL10.EGL_NO_CONTEXT, attribs30)
            if (context != null && context != EGL10.EGL_NO_CONTEXT) {
                Log.i(TAG, "Created OpenGL ES 3.0 context.")
                return context
            }

            Log.e(TAG, "Failed to create OpenGL ES 3.0 or 3.2 context.")
            throw GLESVersionUnsupportedException(
                "OpenGL ES 3.0",
                getDetectedRenderableType(egl, display, eglConfig),
                deviceName(),
                Build.HARDWARE,
                androidVersion(),
            )
        }

        /**
         * Reads the config's EGL_RENDERABLE_TYPE bitmask to report the highest ES version the hardware actually
         * advertises, since no context (and thus no GL_VERSION string) exists at this point.
         */
        private fun getDetectedRenderableType(egl: EGL10, display: EGLDisplay, eglConfig: EGLConfig): String {
            val value = IntArray(1)
            if (!egl.eglGetConfigAttrib(display, eglConfig, EGL10.EGL_RENDERABLE_TYPE, value)) {
                return "Unknown"
            }

            val renderableType = value[0]
            return when {
                renderableType and EGL_OPENGL_ES3_BIT_KHR != 0 -> "OpenGL ES 3.x"
                renderableType and EGL_OPENGL_ES2_BIT != 0 -> "OpenGL ES 2.0"
                renderableType and EGL_OPENGL_ES_BIT != 0 -> "OpenGL ES 1.x"
                else -> "unbekannt"
            }
        }

        override fun destroyContext(egl: EGL10, display: EGLDisplay, context: EGLContext) {
            egl.eglDestroyContext(display, context)
        }
    }

    /**
     * Reads every config the display offers and lets [EglConfigLadder] pick one.
     */
    private class LadderConfigChooser : EGLConfigChooser {

        override fun chooseConfig(egl: EGL10, display: EGLDisplay): EGLConfig {
            val count = IntArray(1)
            val configs = if (egl.eglGetConfigs(display, null, 0, count) && count[0] > 0) {
                val all = arrayOfNulls<EGLConfig>(count[0])
                if (egl.eglGetConfigs(display, all, all.size, count)) all.take(count[0]).filterNotNull() else emptyList()
            } else {
                emptyList()
            }

            val tuples = configs.map { config -> IntArray(ATTRIBUTES.size) { i -> getConfigAttrib(egl, display, config, ATTRIBUTES[i]) } }
            val index = EglConfigLadder.pick(tuples)
                ?: throw GLESVersionUnsupportedException("OpenGL ES 3.0", "no EGL config", deviceName(), Build.HARDWARE, androidVersion())

            val t = tuples[index]
            Log.i(TAG, "Chose EGL config: red=${t[0]} green=${t[1]} blue=${t[2]} alpha=${t[3]} depth=${t[4]} stencil=${t[5]} renderableType=0x${Integer.toHexString(t[6])}")

            return configs[index]
        }

        private fun getConfigAttrib(egl: EGL10, display: EGLDisplay, config: EGLConfig, attribute: Int): Int {
            val value = IntArray(1)
            return if (egl.eglGetConfigAttrib(display, config, attribute, value)) value[0] else 0
        }

        private companion object {
            val ATTRIBUTES = intArrayOf(
                EGL10.EGL_RED_SIZE,
                EGL10.EGL_GREEN_SIZE,
                EGL10.EGL_BLUE_SIZE,
                EGL10.EGL_ALPHA_SIZE,
                EGL10.EGL_DEPTH_SIZE,
                EGL10.EGL_STENCIL_SIZE,
                EGL10.EGL_RENDERABLE_TYPE,
            )
        }
    }

    private companion object {
        const val TAG = "GameSurfaceView"

        /** Standard EGL attribute for the context major version (same as EGL_CONTEXT_CLIENT_VERSION). */
        const val EGL_CONTEXT_CLIENT_VERSION = 0x3098

        /** KHR extension attribute for minor version; equals EGL_CONTEXT_MINOR_VERSION_KHR = 0x30FB. */
        const val EGL_CONTEXT_MINOR_VERSION_KHR = 0x30FB

        fun deviceName() = Build.MANUFACTURER + " " + Build.MODEL

        fun androidVersion() = Build.VERSION.RELEASE + " (SDK " + Build.VERSION.SDK_INT + ")"
    }
}

/** EGL_RENDERABLE_TYPE bits, used to pick a config and to report the ES version a config advertises. */
private const val EGL_OPENGL_ES_BIT = 0x0001
private const val EGL_OPENGL_ES2_BIT = 0x0004
private const val EGL_OPENGL_ES3_BIT_KHR = 0x0040

/**
 * Picks an EGL config from tuples of `[red, green, blue, alpha, depth, stencil, renderableType]`, walking the ladder
 * AndEngine's `ConfigChooser` walked, with RGBA8 and ES3 values: depth 24 + stencil 8, then depth 24 with any stencil,
 * then depth of at least 16 with any stencil (all three RGBA 8/8/8/8 with the ES3 bit), then any config. Each rung
 * returns its first match; `null` only when there are no configs.
 */
object EglConfigLadder {

    private val rungs: List<(IntArray) -> Boolean> = listOf(
        { c -> isRgba8Es3(c) && c[4] == 24 && c[5] == 8 },
        { c -> isRgba8Es3(c) && c[4] == 24 },
        { c -> isRgba8Es3(c) && c[4] >= 16 },
    )

    fun pick(configs: List<IntArray>): Int? {
        if (configs.isEmpty()) {
            return null
        }

        for (rung in rungs) {
            val index = configs.indexOfFirst(rung)
            if (index >= 0) {
                return index
            }
        }

        return 0
    }

    private fun isRgba8Es3(c: IntArray) =
        c[0] == 8 && c[1] == 8 && c[2] == 8 && c[3] == 8 && c[6] and EGL_OPENGL_ES3_BIT_KHR != 0
}

/**
 * `RatioResolutionPolicy.onMeasure`'s arithmetic: the largest size of [ratio] that fits the spec. A ratio of 0 (or
 * less) fills the spec.
 */
internal object RatioMeasure {

    fun measure(ratio: Float, specWidth: Int, specHeight: Int): IntArray {
        if (ratio <= 0f) {
            return intArrayOf(specWidth, specHeight)
        }

        val realRatio = specWidth.toFloat() / specHeight

        return if (realRatio < ratio) {
            intArrayOf(specWidth, (specWidth / ratio).roundToInt())
        } else {
            intArrayOf((specHeight * ratio).roundToInt(), specHeight)
        }
    }
}
