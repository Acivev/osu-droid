package com.osudroid.verktex

import com.reco1l.verktex.BuiltIn
import com.reco1l.verktex.graphics.DrawCommand
import com.reco1l.verktex.graphics.GraphicsBuffer
import com.reco1l.verktex.graphics.GraphicsContext
import com.reco1l.verktex.graphics.GraphicsDevice
import com.reco1l.verktex.graphics.GraphicsRenderer
import com.reco1l.verktex.graphics.GraphicsShader
import com.reco1l.verktex.graphics.GraphicsTexture
import com.reco1l.verktex.graphics.GraphicsWindow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * A depth clear is recorded as its own command between draws, so the batch is never merged across it.
 */
class GraphicsRendererCommandsTest {

    private class FakeBuffer : GraphicsBuffer() {
        override val capacity get() = Int.MAX_VALUE
        override var position = 0
        override fun clear() { position = 0 }
        override fun requireCapacity(requiredBytes: Int) = Unit
        override fun putFloat(float: Float) { position += 4 }
        override fun putByte(byte: Byte) { position += 1 }
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

    private class FakeTexture(override val width: Float, override val height: Float) : GraphicsTexture() {
        override fun onCreateTexture() = Unit
        override fun onBindTexture() = Unit
        override fun onUnload() = Unit
    }

    private object FakeDevice : GraphicsDevice {
        override val capabilities = GraphicsDevice.Capabilities(maxTextureSize = 4096f)
        override fun createBuffer(): GraphicsBuffer = FakeBuffer()
        override fun createShader(source: GraphicsShader.Source): GraphicsShader = FakeShader()
        override fun createTexture(width: Float, height: Float): GraphicsTexture = FakeTexture(width, height)
    }

    private class RecordingRenderer : GraphicsRenderer() {
        override val device: GraphicsDevice = FakeDevice
        override val context: GraphicsContext get() = throw UnsupportedOperationException()
        override val window: GraphicsWindow get() = throw UnsupportedOperationException()
        override var version = "fake"

        val commands = mutableListOf<DrawCommand>()

        override fun drawCommand(command: DrawCommand) {
            commands += command
        }
    }

    private val solidQuad = FakeShader()

    @Before
    fun setUp() {
        BuiltIn.Shaders.SolidQuad = solidQuad
    }

    @Test
    fun clearDepthSplitsTheBatchAndKeepsOrder() {
        val renderer = RecordingRenderer()

        renderer.begin()
        renderer.drawRect(0f, 0f, 10f, 10f)
        renderer.clearDepth()
        renderer.drawRect(0f, 0f, 10f, 10f)
        renderer.end()

        val commands = renderer.commands
        assertEquals(3, commands.size)

        assertFalse(commands[0].clearDepth)
        assertTrue(commands[1].clearDepth)
        assertNull(commands[1].buffer)
        assertNull(commands[1].shader)
        assertFalse(commands[2].clearDepth)

        // The draw after the clear was not merged back into the first command.
        assertNotSame(commands[0].buffer, commands[2].buffer)
        assertEquals(VERTICES_PER_RECT * GraphicsRenderer.VERTEX_2D_STRIDE, commands[0].buffer!!.position)
        assertEquals(VERTICES_PER_RECT * GraphicsRenderer.VERTEX_2D_STRIDE, commands[2].buffer!!.position)
    }

    @Test
    fun drawsWithTheSameStateMergeWithoutAClear() {
        val renderer = RecordingRenderer()

        renderer.begin()
        renderer.drawRect(0f, 0f, 10f, 10f)
        renderer.drawRect(0f, 0f, 10f, 10f)
        renderer.end()

        assertEquals(1, renderer.commands.size)
    }

    @Test
    fun clearDepthWithNoDrawsStillRecords() {
        val renderer = RecordingRenderer()

        renderer.begin()
        renderer.clearDepth()
        renderer.end()

        assertEquals(1, renderer.commands.size)
        assertTrue(renderer.commands[0].clearDepth)
        assertNull(renderer.commands[0].buffer)
    }

    private companion object {
        const val VERTICES_PER_RECT = 6
    }
}
