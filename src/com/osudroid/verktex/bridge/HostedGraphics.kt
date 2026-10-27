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
import com.reco1l.verktex.android.gles.ESCommandExecutor
import com.reco1l.verktex.android.gles.ESDevice
import com.reco1l.verktex.data.Vec2
import com.reco1l.verktex.data.Vec4
import com.reco1l.verktex.graphics.DrawCommand
import com.reco1l.verktex.graphics.GraphicsContext
import com.reco1l.verktex.graphics.GraphicsRenderer
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

// Twin of com.reco1l.verktex.android.gles.ESRenderer: draw commands run through ESCommandExecutor; only context/window/clear/swap differ.
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
        ESCommandExecutor.execute(command, window.windowWidth, window.windowHeight)
    }
}
