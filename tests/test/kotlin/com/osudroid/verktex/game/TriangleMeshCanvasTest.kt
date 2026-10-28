package com.osudroid.verktex.game

import com.osudroid.ui.verktex.TriangleMeshCanvas
import com.osudroid.verktex.VerktexTestFixtures
import com.reco1l.verktex.BuiltIn
import com.reco1l.verktex.data.Color4
import com.reco1l.verktex.graphics.DrawCommand
import com.reco1l.verktex.graphics.GraphicsBuffer
import com.reco1l.verktex.graphics.GraphicsContext
import com.reco1l.verktex.graphics.GraphicsDevice
import com.reco1l.verktex.graphics.GraphicsRenderer
import com.reco1l.verktex.graphics.GraphicsShader
import com.reco1l.verktex.graphics.GraphicsTexture
import com.reco1l.verktex.graphics.GraphicsWindow
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * `GraphicsRenderer.drawTriangles` and `clearDepth` are final, so the draw is recorded through the
 * commands they queue: a depth clear is its own command, the mesh writes one vertex per position.
 */
class TriangleMeshCanvasTest {

    private class RecordingBuffer : GraphicsBuffer() {
        override val capacity get() = Int.MAX_VALUE
        override var position = 0
        val bytes = mutableListOf<Byte>()
        override fun clear() { position = 0; bytes.clear() }
        override fun requireCapacity(requiredBytes: Int) = Unit
        override fun putFloat(float: Float) { position += 4 }
        override fun putByte(byte: Byte) { position += 1; bytes += byte }
        override fun uploadDataToGPU() = Unit
        override fun onBind() = Unit
        override fun onLoad() = Unit
        override fun onUnload() = Unit
    }

    private class FakeShader : GraphicsShader() {
        override val source = Source("", "")
        override fun applyUniforms(uniformSet: UniformSet) = Unit
        override fun onBind() = Unit
        override fun onLoad() = Unit
        override fun onUnload() = Unit
    }

    private object RecordingDevice : GraphicsDevice {
        override val capabilities = GraphicsDevice.Capabilities(maxTextureSize = 4096f)
        override fun createBuffer(): GraphicsBuffer = RecordingBuffer()
        override fun createShader(source: GraphicsShader.Source): GraphicsShader = throw UnsupportedOperationException()
        override fun createTexture(width: Float, height: Float): GraphicsTexture = throw UnsupportedOperationException()
        override fun createExternalTexture(width: Float, height: Float): GraphicsTexture = throw UnsupportedOperationException()
    }

    private class RecordingRenderer : GraphicsRenderer() {
        override val device: GraphicsDevice = RecordingDevice
        override val context: GraphicsContext get() = throw UnsupportedOperationException()
        override val window: GraphicsWindow get() = throw UnsupportedOperationException()
        override var version = "fake"

        val commands = mutableListOf<DrawCommand>()

        override fun drawCommand(command: DrawCommand) {
            commands += command
        }
    }

    private val triangle = floatArrayOf(0f, 0f, 10f, 0f, 0f, 10f)
    private var previousSolidQuad: GraphicsShader? = null

    @Before
    fun setUp() {
        VerktexTestFixtures.ensureInitialized()

        // The built-in shaders are lateinit, reading one that was never set throws.
        previousSolidQuad = try { BuiltIn.Shaders.SolidQuad } catch (_: UninitializedPropertyAccessException) { null }
        BuiltIn.Shaders.SolidQuad = FakeShader()
    }

    @After
    fun tearDown() {
        previousSolidQuad?.let { BuiltIn.Shaders.SolidQuad = it }
    }

    private fun drawn(canvas: TriangleMeshCanvas): List<DrawCommand> {
        val renderer = RecordingRenderer()
        renderer.begin()
        canvas.draw(renderer)
        renderer.end()
        return renderer.commands
    }

    @Test
    fun drawsNothingWhenEmpty() {
        val canvas = TriangleMeshCanvas()
        assertTrue(drawn(canvas).isEmpty())

        canvas.setVertices(triangle, 3)
        canvas.clear()
        assertEquals(0, canvas.vertexCount)
        assertTrue(drawn(canvas).isEmpty())
    }

    @Test
    fun drawsTheVertexCountWithTheComponentColorAndAlpha() {
        val canvas = TriangleMeshCanvas()
        canvas.color = Color4(1f, 0f, 0f)
        canvas.alpha = 0.5f
        canvas.setVertices(triangle, 3)

        val command = drawn(canvas).single()
        val buffer = command.buffer as RecordingBuffer
        assertEquals(3 * GraphicsRenderer.VERTEX_2D_STRIDE, buffer.position)

        val expected = Color4(1f, 0f, 0f, 0.5f)
        val expectedBytes = listOf(expected.red8, expected.green8, expected.blue8, expected.alpha8).map { it.toByte() }
        val colors = buffer.bytes.chunked(4)
        assertEquals(3, colors.size)
        colors.forEach { assertEquals(expectedBytes, it) }
    }

    @Test
    fun clearDepthFlagClearsBeforeTheDraw() {
        val canvas = TriangleMeshCanvas()
        canvas.clearDepthBeforeDraw = true
        canvas.setVertices(triangle, 3)

        val commands = drawn(canvas)
        assertEquals(2, commands.size)
        assertTrue(commands[0].clearDepth)
        assertFalse(commands[1].clearDepth)
        assertEquals(3 * GraphicsRenderer.VERTEX_2D_STRIDE, commands[1].buffer!!.position)
    }
}
