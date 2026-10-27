package com.osudroid.verktex

import com.osudroid.verktex.bridge.ManualLoopWorker
import com.reco1l.verktex.GameHost
import com.reco1l.verktex.Logger
import com.reco1l.verktex.Platform
import com.reco1l.verktex.Provider
import com.reco1l.verktex.Verktex
import com.reco1l.verktex.audio.AudioDevice
import com.reco1l.verktex.audio.AudioDriver
import com.reco1l.verktex.audio.AudioSample
import com.reco1l.verktex.audio.AudioStream
import com.reco1l.verktex.data.Vec2
import com.reco1l.verktex.data.Vec4
import com.reco1l.verktex.fonts.FontFamily
import com.reco1l.verktex.graphics.DrawCommand
import com.reco1l.verktex.graphics.GraphicsBuffer
import com.reco1l.verktex.graphics.GraphicsContext
import com.reco1l.verktex.graphics.GraphicsDevice
import com.reco1l.verktex.graphics.GraphicsRenderer
import com.reco1l.verktex.graphics.GraphicsShader
import com.reco1l.verktex.graphics.GraphicsTexture
import com.reco1l.verktex.graphics.GraphicsWindow
import com.reco1l.verktex.ui.ITextInput

/**
 * No-op implementations of the Verktex platform interfaces so engine logic can run on the JVM.
 *
 * [Verktex] is a process-wide singleton, so every test in the JVM shares the fakes created by
 * [ensureInitialized]. Tests must use unique texture keys and unload what they load.
 */
object VerktexTestFixtures {

    class FakeSource(override val width: Float, override val height: Float) : GraphicsTexture.Source {
        override fun uploadToHardware(texturePositionX: Float, texturePositionY: Float) = Unit
    }

    class FakeTexture(override val width: Float, override val height: Float) : GraphicsTexture() {

        var disposed = false

        override fun onCreateTexture() = Unit

        override fun onBindTexture() = Unit

        override fun onUnload() = Unit

        override fun dispose() {
            disposed = true
            super.dispose()
        }
    }

    private class FakeBuffer : GraphicsBuffer() {
        override val capacity = 0
        override val position = 0

        override fun clear() = Unit
        override fun requireCapacity(requiredBytes: Int) = Unit
        override fun putFloat(float: Float) = Unit
        override fun putByte(byte: Byte) = Unit
        override fun uploadDataToGPU() = Unit

        override fun onBind() = Unit
        override fun onLoad() = Unit
        override fun onUnload() = Unit
    }

    private class FakeShader(override val source: Source) : GraphicsShader() {
        override fun applyUniforms(uniformSet: UniformSet) = Unit

        override fun onBind() = Unit
        override fun onLoad() = Unit
        override fun onUnload() = Unit
    }

    class FakeDevice(maxTextureSize: Float) : GraphicsDevice {
        override val capabilities = GraphicsDevice.Capabilities(maxTextureSize)

        override fun createBuffer(): GraphicsBuffer = FakeBuffer()

        override fun createShader(source: GraphicsShader.Source): GraphicsShader = FakeShader(source)

        override fun createTexture(width: Float, height: Float): GraphicsTexture = FakeTexture(width, height)
    }

    private object FakeContext : GraphicsContext {
        override val version = "fake"

        override fun makeCurrent() = Unit
        override fun swapBuffers() = Unit
        override fun destroy() = Unit
    }

    private object FakeWindow : GraphicsWindow {
        override var windowWidth = 1280
        override var windowHeight = 720
        override var insets = GraphicsWindow.Insets(Vec4(0f, 0f, 0f, 0f), Vec2(0f, 0f))

        override fun shouldClose() = false
        override fun pollEvents() = Unit
    }

    class FakeRenderer(override val device: GraphicsDevice) : GraphicsRenderer() {
        override val context: GraphicsContext = FakeContext
        override val window: GraphicsWindow = FakeWindow
        override var version = "fake"

        override fun drawCommand(command: DrawCommand) = Unit
    }

    class FakeProvider : Provider {

        /**
         * Sizes of the textures this provider can create sources for, keyed by path.
         */
        val textureSizes = mutableMapOf<String, Pair<Float, Float>>()

        override fun exists(path: String, isExternal: Boolean) = path in textureSizes

        override fun createFontFamilySource(path: String, isExternal: Boolean): FontFamily.Source? = null

        override fun createTextureSource(path: String, isExternal: Boolean): GraphicsTexture.Source? =
            textureSizes[path]?.let { (width, height) -> FakeSource(width, height) }

        override fun createShaderSource(
            vertexShaderPath: String,
            fragmentShaderPath: String,
            isExternal: Boolean
        ) = GraphicsShader.Source("", "")

        override fun createAudioSampleSource(path: String, isExternal: Boolean): AudioSample.Source? = null

        override fun createAudioStreamSource(path: String, isExternal: Boolean): AudioStream.Source? = null
    }

    class FakePlatform(override val provider: FakeProvider) : Platform.Implementation {

        override val currentDisplay = Platform.Display(1f, 1280, 720)

        override val usesVirtualKeyboard = false

        val workers = mutableMapOf<String, ManualLoopWorker>()

        override fun createLoopWorker(name: String, onLoop: () -> Unit) =
            ManualLoopWorker(name, onLoop).also { workers[name] = it }
    }

    private object FakeHost : GameHost {
        override fun onEngineInitialized() = Unit
        override fun onEngineStarted() = Unit
        override fun attachTextInput(textInput: ITextInput) = Unit
        override fun detachTextInput() = Unit
    }

    private object FakeAudioDriver : AudioDriver() {
        override fun onEnumerateDevices(): List<AudioDevice> = emptyList()
    }


    private var provider: FakeProvider? = null
    private var platform: FakePlatform? = null

    /**
     * Runs what was scheduled with [Verktex.scheduleOnDraw], such as deferred texture disposal.
     */
    fun pumpDraw() {
        platform!!.workers.getValue("draw_worker").pump()
    }

    /**
     * Sets up logging, the platform and the engine with fakes. Safe to call from every test, only the
     * first call in the JVM has any effect.
     *
     * @return The provider used by the platform, register texture paths in [FakeProvider.textureSizes].
     */
    @Synchronized
    fun ensureInitialized(maxTextureSize: Float = 4096f): FakeProvider {
        provider?.let { return it }

        Logger.implementation = object : Logger.Implementation {
            override fun i(tag: String, message: String) = println("I/$tag: $message")
            override fun w(tag: String, message: String) = println("W/$tag: $message")
            override fun e(tag: String, message: String, throwable: Throwable?) {
                println("E/$tag: $message")
                throwable?.printStackTrace()
            }
        }

        val fakeProvider = FakeProvider()
        val fakePlatform = FakePlatform(fakeProvider)
        platform = fakePlatform
        Platform.implementation = fakePlatform

        try {
            Verktex.initialize(FakeHost, FakeRenderer(FakeDevice(maxTextureSize)), FakeAudioDriver)

            // Scheduling needs a started engine; the workers are manual so nothing runs until pumped.
            Verktex.start()
        } catch (_: IllegalStateException) {
            // Already initialized and started by another fixture user in this JVM, reuse it.
        }

        provider = fakeProvider
        return fakeProvider
    }
}
