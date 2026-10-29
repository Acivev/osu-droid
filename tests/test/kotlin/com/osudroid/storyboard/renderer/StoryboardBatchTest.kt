package com.osudroid.storyboard.renderer

import com.osudroid.storyboard.model.StoryboardLayerType
import com.osudroid.storyboard.model.StoryboardOrigin
import com.osudroid.storyboard.model.StoryboardSprite
import com.osudroid.storyboard.playback.PlayableSprite
import com.reco1l.verktex.BuiltIn
import com.reco1l.verktex.data.Color4
import com.reco1l.verktex.data.Mat2
import com.reco1l.verktex.graphics.BlendMode
import com.reco1l.verktex.graphics.DrawCommand
import com.reco1l.verktex.graphics.GraphicsBuffer
import com.reco1l.verktex.graphics.GraphicsContext
import com.reco1l.verktex.graphics.GraphicsDevice
import com.reco1l.verktex.graphics.GraphicsRenderer
import com.reco1l.verktex.graphics.GraphicsShader
import com.reco1l.verktex.graphics.GraphicsTexture
import com.reco1l.verktex.graphics.GraphicsWindow
import kotlin.math.PI
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Before
import org.junit.Test

/**
 * The batch keeps the CPU maths of the AndEngine batch and emits its quads through the Verktex renderer:
 * one texture-quad command per texture and blend run, positions through the renderer's matrix and alpha
 * multiplied by its base colour.
 */
class StoryboardBatchTest {

    /**
     * One vertex as the batch wrote it: position and UV as floats, the colour as 8-bit channels.
     */
    private data class Vertex(val x: Float, val y: Float, val u: Float, val v: Float, val r: Int, val g: Int, val b: Int, val a: Int)

    private class RecordingBuffer : GraphicsBuffer() {
        override val capacity get() = Int.MAX_VALUE
        override var position = 0
        val floats = mutableListOf<Float>()
        val bytes = mutableListOf<Int>()
        override fun clear() { position = 0; floats.clear(); bytes.clear() }
        override fun requireCapacity(requiredBytes: Int) = Unit
        override fun putFloat(float: Float) { position += 4; floats += float }
        override fun putByte(byte: Byte) { position += 1; bytes += byte.toInt() and 0xFF }
        override fun uploadDataToGPU() = Unit
        override fun onBind() = Unit
        override fun onLoad() = Unit
        override fun onUnload() = Unit

        fun vertices() = floats.chunked(4).zip(bytes.chunked(4)) { f, c -> Vertex(f[0], f[1], f[2], f[3], c[0], c[1], c[2], c[3]) }
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

    private object NoSource : GraphicsTexture.Source {
        override val width = 1f
        override val height = 1f
        override fun uploadToHardware(texturePositionX: Float, texturePositionY: Float) = Unit
    }

    private object FakeDevice : GraphicsDevice {
        override val capabilities = GraphicsDevice.Capabilities(maxTextureSize = 4096f)
        override fun createBuffer(): GraphicsBuffer = RecordingBuffer()
        override fun createShader(source: GraphicsShader.Source): GraphicsShader = FakeShader()
        override fun createTexture(width: Float, height: Float): GraphicsTexture = FakeTexture(width, height)
        override fun createExternalTexture(width: Float, height: Float): GraphicsTexture = FakeTexture(width, height)
    }

    /**
     * Keeps every command with a copy of its vertices, read when the renderer's end() hands them over.
     */
    private class RecordingRenderer : GraphicsRenderer() {
        override val device: GraphicsDevice = FakeDevice
        override val context: GraphicsContext get() = throw UnsupportedOperationException()
        override val window: GraphicsWindow get() = throw UnsupportedOperationException()
        override var version = "fake"

        val commands = mutableListOf<DrawCommand>()
        val vertices = mutableListOf<List<Vertex>>()

        override fun drawCommand(command: DrawCommand) {
            commands += command
            vertices += (command.buffer as RecordingBuffer).vertices()
        }
    }

    private val textureQuad = FakeShader()
    private var previousTextureQuad: GraphicsShader? = null

    private val texture = FakeTexture(200f, 100f)
    private val region = GraphicsTexture.Region(texture, NoSource, 0f, 0f, 100f, 50f)

    private val renderer = RecordingRenderer()
    private val batch = StoryboardBatch()

    @Before
    fun setUp() {
        // The built-in shaders are lateinit, reading one that was never set throws.
        previousTextureQuad = try { BuiltIn.Shaders.TextureQuad } catch (_: UninitializedPropertyAccessException) { null }
        BuiltIn.Shaders.TextureQuad = textureQuad
        renderer.begin()
    }

    @After
    fun tearDown() {
        previousTextureQuad?.let { BuiltIn.Shaders.TextureQuad = it }
    }

    private fun sprite(origin: StoryboardOrigin = StoryboardOrigin.Centre, x: Float = 320f, y: Float = 240f) =
        PlayableSprite(StoryboardSprite(StoryboardLayerType.Foreground, origin, "sprite.png", x, y)).also {
            it.x = x
            it.y = y
        }

    private fun drawAndEnd(block: StoryboardBatch.() -> Unit) {
        batch.begin(renderer)
        batch.block()
        batch.end()
        renderer.end()
    }

    private fun assertVertex(expected: Vertex, actual: Vertex) {
        assertEquals(expected.x, actual.x, EPSILON)
        assertEquals(expected.y, actual.y, EPSILON)
        assertEquals(expected.u, actual.u, EPSILON)
        assertEquals(expected.v, actual.v, EPSILON)
        assertEquals(listOf(expected.r, expected.g, expected.b, expected.a), listOf(actual.r, actual.g, actual.b, actual.a))
    }

    @Test
    fun unrotatedSpriteVerticesMatchOriginAndScale() {
        val sprite = sprite().also {
            it.scaleX = 2f
            it.scaleY = 2f
            it.red = 1f
            it.green = 0.5f
            it.blue = 0.25f
            it.alpha = 0.8f
        }

        drawAndEnd {
            setSpace(10f, 20f, 0.5f)
            draw(sprite, region, 1f)
        }

        // Left: 10 + 320 * 0.5 - 100 * 0.5 * 2 * 0.5, top: 20 + 240 * 0.5 - 50 * 0.5 * 2 * 0.5.
        assertVertex(Vertex(120f, 115f, 0f, 0f, 255, 128, 64, 204), renderer.vertices[0][0])
        assertEquals(6, renderer.vertices[0].size)
        // The bottom right corner, the second vertex of the second triangle.
        assertVertex(Vertex(220f, 165f, 0.5f, 0.5f, 255, 128, 64, 204), renderer.vertices[0][4])
    }

    @Test
    fun mirrorFlipsTheUvs() {
        val sprite = sprite().also {
            it.flipHorizontal = true
            it.flipVertical = true
        }

        drawAndEnd { draw(sprite, region, 1f) }

        val first = renderer.vertices[0][0]
        assertEquals(0.5f, first.u, EPSILON)
        assertEquals(0.5f, first.v, EPSILON)
        // The geometry stays in place.
        assertEquals(270f, first.x, EPSILON)
        assertEquals(215f, first.y, EPSILON)
    }

    @Test
    fun negativeScaleAndFlipCancel() {
        val sprite = sprite().also {
            it.scaleX = -1f
            it.flipHorizontal = true
        }

        drawAndEnd { draw(sprite, region, 1f) }

        val first = renderer.vertices[0][0]
        assertEquals(0f, first.u, EPSILON)
        assertEquals(270f, first.x, EPSILON)
    }

    @Test
    fun rotationAppliesAroundTheOrigin() {
        val small = GraphicsTexture.Region(texture, NoSource, 0f, 0f, 10f, 20f)
        val sprite = sprite(StoryboardOrigin.TopLeft, 100f, 100f).also { it.rotation = (PI / 2).toFloat() }

        drawAndEnd { draw(sprite, small, 1f) }

        // Corners (l, t), (r, t), (l, b), (r, b) rotated by 90 degrees around the top left origin, as the
        // two triangles (0, 1, 2) and (1, 3, 2).
        val positions = renderer.vertices[0].map { it.x to it.y }
        val expected = listOf(100f to 100f, 100f to 110f, 80f to 100f, 100f to 110f, 80f to 110f, 80f to 100f)

        assertEquals(expected.size, positions.size)

        for ((e, a) in expected.zip(positions)) {
            assertEquals(e.first, a.first, 1e-3f)
            assertEquals(e.second, a.second, 1e-3f)
        }
    }

    @Test
    fun additiveQuadsGetTheirOwnCommand() {
        drawAndEnd {
            draw(sprite(), region, 1f)
            draw(sprite().also { it.additiveBlend = true }, region, 1f)
            draw(sprite(), region, 1f)
        }

        assertEquals(listOf(BlendMode.Alpha, BlendMode.Additive, BlendMode.Alpha), renderer.commands.map { it.blendMode })
        assertEquals(listOf(6, 6, 6), renderer.vertices.map { it.size })
    }

    @Test
    fun textureChangeSplitsTheCommand() {
        val other = GraphicsTexture.Region(FakeTexture(64f, 64f), NoSource, 0f, 0f, 64f, 64f)

        // A rounded draw before the storyboard must not leak its uniforms into the quads.
        renderer.setState(shader = FakeShader(), uniformSet = GraphicsShader.UniformSet().set("uRadius", 4f))

        drawAndEnd {
            draw(sprite(), region, 1f)
            draw(sprite(), region, 1f)
            drawQuad(other, 0f, 0f, 10f, 10f, 0f, 0f, 0f, 1f)
        }

        val commands = renderer.commands.drop(1)
        assertEquals(2, commands.size)
        assertSame(texture, commands[0].texture)
        assertSame(other.texture, commands[1].texture)
        assertEquals(listOf(12, 6), renderer.vertices.drop(1).map { it.size })

        for (command in commands) {
            assertSame(textureQuad, command.shader)
            assertEquals(GraphicsShader.UniformSet(), command.uniformSet)
        }
    }

    @Test
    fun alphaIsMultipliedByTheBaseColour() {
        renderer.pushColor(Color4(1f, 1f, 1f, 0.5f))

        drawAndEnd {
            draw(sprite().also { it.alpha = 0.8f }, region, 1f)
            drawQuad(region, 0f, 0f, 10f, 10f, 0f, 0f, 0f, 1f)
        }

        val vertices = renderer.vertices[0]
        assertEquals(102, vertices[0].a)
        assertEquals(128, vertices[6].a)
    }

    @Test
    fun positionsGoThroughTheRendererMatrix() {
        renderer.pushMatrix(Mat2(a = 2f, d = 2f, tx = 5f, ty = 7f))

        drawAndEnd { drawQuad(region, 1f, 2f, 3f, 4f, 1f, 1f, 1f, 1f) }

        val first = renderer.vertices[0][0]
        assertEquals(7f, first.x, EPSILON)
        assertEquals(11f, first.y, EPSILON)
    }

    @Test
    fun flushAt1024Quads() {
        batch.begin(renderer)

        repeat(1023) { batch.draw(sprite(), region, 1f) }
        assertEquals(0, renderer.commandCount)

        batch.draw(sprite(), region, 1f)
        assertEquals(1, renderer.commandCount)

        batch.draw(sprite(), region, 1f)
        batch.end()
        renderer.end()

        // The second flush has the same state, so it merges into the first command.
        assertEquals(1, renderer.commands.size)
        assertEquals(1025 * 6, renderer.vertices[0].size)
    }

    private companion object {
        const val EPSILON = 1e-4f
    }
}
