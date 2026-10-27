package com.osudroid.verktex.bridge

import android.opengl.GLES32
import android.os.Build
import android.view.RoundedCorner
import android.view.View
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.reco1l.verktex.BuiltIn
import com.reco1l.verktex.Logger
import com.reco1l.verktex.Platform
import com.reco1l.verktex.android.gles.ESDevice
import com.reco1l.verktex.android.gles.toGLES
import com.reco1l.verktex.android.gles.toGLESDestination
import com.reco1l.verktex.android.gles.toGLESSource
import com.reco1l.verktex.data.Color4
import com.reco1l.verktex.data.Vec2
import com.reco1l.verktex.data.Vec4
import com.reco1l.verktex.graphics.DrawCommand
import com.reco1l.verktex.graphics.GraphicsContext
import com.reco1l.verktex.graphics.GraphicsRenderer
import com.reco1l.verktex.graphics.GraphicsShader
import com.reco1l.verktex.graphics.GraphicsWindow
import kotlin.math.max

/**
 * A [GraphicsContext] for a host that owns the EGL context, nothing is managed here.
 */
class HostedContext : GraphicsContext {

    override val version = "hosted by AndEngine"

    override fun makeCurrent() {}

    override fun swapBuffers() {}

    override fun destroy() {}
}

/**
 * A [GraphicsWindow] whose size is set by the host.
 */
class HostedWindow : GraphicsWindow {

    override var windowWidth: Int = 0
    override var windowHeight: Int = 0
    override var insets = GraphicsWindow.Insets(
        safeArea = Vec4.Zero,
        offsets = Vec2.Zero
    )


    override fun shouldClose() = false

    override fun pollEvents() {
        // no-op
    }

    fun attachInsetsListener(hostView: View) {

        hostView.setOnApplyWindowInsetsListener { view, viewInsets ->

            val compatInsets = ViewCompat.getRootWindowInsets(view)

            var radiusTopLeft = 0
            var radiusTopRight = 0
            var radiusBottomLeft = 0
            var radiusBottomRight = 0

            // Unfortunately there's no backward compatible API to get corner radius.
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val windowInsets = view.getRootWindowInsets()

                val topLeft = windowInsets.getRoundedCorner(RoundedCorner.POSITION_TOP_LEFT)
                val topRight = windowInsets.getRoundedCorner(RoundedCorner.POSITION_TOP_RIGHT)
                val bottomLeft = windowInsets.getRoundedCorner(RoundedCorner.POSITION_BOTTOM_LEFT)
                val bottomRight = windowInsets.getRoundedCorner(RoundedCorner.POSITION_BOTTOM_RIGHT)

                if (topLeft != null) radiusTopLeft = topLeft.radius
                if (topRight != null) radiusTopRight = topRight.radius
                if (bottomLeft != null) radiusBottomLeft = bottomLeft.radius
                if (bottomRight != null) radiusBottomRight = bottomRight.radius
            }

            val leftRadius = max(radiusTopLeft, radiusBottomLeft)
            val rightRadius = max(radiusTopRight, radiusBottomRight)

            val bars = compatInsets?.getInsets(WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout())

            val safeArea = Vec4(
                x = max(bars?.left ?: 0, leftRadius).toFloat(),
                y = 0f,
                z = max(bars?.right ?: 0, rightRadius).toFloat(),
                w = 0f
            )

            val virtualKeyboardHeight = compatInsets?.getInsets(WindowInsetsCompat.Type.ime())?.bottom ?: 0

            insets = GraphicsWindow.Insets(
                safeArea = safeArea,
                offsets = Vec2(0f, -virtualKeyboardHeight.toFloat())
            )

            viewInsets
        }

        // The host view is already attached, so insets would not be dispatched again on their own.
        ViewCompat.requestApplyInsets(hostView)
    }
}

// Twin of com.reco1l.verktex.android.gles.ESRenderer: keep draw/command code in sync; only context/window/clear/swap differ.
class HostedRenderer(
    override val context: HostedContext,
    override val device: ESDevice,
    override val window: HostedWindow,
) : GraphicsRenderer() {

    override var version: String


    init {
        BuiltIn.Shaders.SolidQuad = createShader(
            Platform.provider.createShaderSource(
                vertexShaderPath = "verktex/shaders/main.vert",
                fragmentShaderPath = "verktex/shaders/quad.frag",
                isExternal = false
            ) ?: throw IllegalStateException("Failed to load built-in shader source: verktex/quad.frag and verktex/main.vert")
        )

        BuiltIn.Shaders.TextureQuad = createShader(
            Platform.provider.createShaderSource(
                vertexShaderPath = "verktex/shaders/main.vert",
                fragmentShaderPath = "verktex/shaders/texture.frag",
                isExternal = false
            ) ?: throw IllegalStateException("Failed to load built-in shader source: verktex/texture.frag and verktex/main.vert")
        )

        version = GLES32.glGetString(GLES32.GL_VERSION) ?: run {
            Logger.w("HostedRenderer", "Couldn't retrieve OpenGL ES version.")
            "Unknown OpenGL ES version"
        }
    }

    private var capabilitiesRefreshed = false

    override fun prepareFrame(): Boolean {
        return true
    }

    override fun begin() {
        super.begin()

        // AndEngine's context is current on the draw thread from here on, the device was created without one.
        if (!capabilitiesRefreshed) {
            capabilitiesRefreshed = true
            device.refreshCapabilities()
        }

        // AndEngine leaves array 3 enabled, pointing at its last VBO; Verktex only uses 0..2 and an
        // enabled array with a dangling pointer is undefined behaviour on ES.
        GLES32.glDisableVertexAttribArray(3)
        GLES32.glDisableVertexAttribArray(4)

        GLES32.glViewport(0, 0, window.windowWidth, window.windowHeight)
    }

    override fun end() {
        super.end()
    }

    override fun drawCommand(command: DrawCommand) {

        // Local copies, command properties from another module can't be smart cast.
        val buffer = command.buffer
        val shader = command.shader
        val texture = command.texture

        if (buffer == null || shader == null || buffer.isEmpty()) return

        // Scissor test, the scissor is in window space (top-left origin) while GL expects bottom-left.
        val scissor = command.scissor
        if (scissor != null) {
            GLES32.glEnable(GLES32.GL_SCISSOR_TEST)
            GLES32.glScissor(
                scissor.x.toInt(),
                (window.windowHeight - scissor.y - scissor.w).toInt(),
                scissor.z.toInt().coerceAtLeast(0),
                scissor.w.toInt().coerceAtLeast(0)
            )
        } else {
            GLES32.glDisable(GLES32.GL_SCISSOR_TEST)
        }

        GLES32.glDisable(GLES32.GL_CULL_FACE)

        // Blend
        GLES32.glEnable(GLES32.GL_BLEND)
        GLES32.glBlendFunc(
            command.blendMode.toGLESSource(),
            command.blendMode.toGLESDestination()
        )

        // Depth testing
        if (command.depthState.enabled) {
            GLES32.glEnable(GLES32.GL_DEPTH_TEST)
            GLES32.glDepthFunc(command.depthState.function.toGLES())
            GLES32.glDepthMask(command.depthState.writeEnabled)
        } else {
            GLES32.glDisable(GLES32.GL_DEPTH_TEST)
            GLES32.glDepthMask(false)
        }

        shader.bind()

        if (texture != null) {
            GLES32.glActiveTexture(GLES32.GL_TEXTURE0)
            texture.bind()
        }

        shader.applyUniforms(GraphicsShader.UniformSet().apply {
            // Uniform values persist per program, so reset them to defaults before every draw.
            val viewportSize = Vec2(window.windowWidth.toFloat(), window.windowHeight.toFloat())
            set("uViewportSize", viewportSize)
            set("uColor", Color4.White)
            set("uTexture", 0)
            set("uRadius", 0f)
            set("uBorderWidth", 0f)
            set("uBorderColor", Color4.Transparent)
            set("uSize", viewportSize)
        })
        shader.applyUniforms(command.uniformSet)

        buffer.bind()
        buffer.uploadDataToGPU()

        // Vertex layout: position (2 floats), UV (2 floats), color (4 normalized unsigned bytes).
        GLES32.glEnableVertexAttribArray(0)
        GLES32.glVertexAttribPointer(0, 2, GLES32.GL_FLOAT, false, VERTEX_2D_STRIDE, 0)
        GLES32.glEnableVertexAttribArray(1)
        GLES32.glVertexAttribPointer(1, 2, GLES32.GL_FLOAT, false, VERTEX_2D_STRIDE, 8)
        GLES32.glEnableVertexAttribArray(2)
        GLES32.glVertexAttribPointer(2, 4, GLES32.GL_UNSIGNED_BYTE, true, VERTEX_2D_STRIDE, 16)

        val vertexCount = buffer.position / VERTEX_2D_STRIDE

        GLES32.glDrawArrays(GLES32.GL_TRIANGLES, 0, vertexCount)
    }
}
