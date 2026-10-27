package com.osudroid.verktex

import com.reco1l.verktex.android.gles.ESExternalTexture
import com.reco1l.verktex.graphics.GraphicsShader
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The CPU side of external (video) textures: the mat4 uniform and the frame latch. The GL and
 * SurfaceTexture calls are replaced through the texture's seams.
 */
class ExternalTextureTest {

    private class FakeExternalTexture : ESExternalTexture(320f, 180f) {
        var updates = 0

        override fun onCreateTexture() = Unit
        override fun bindExternalTexture() = Unit
        override fun onUnload() = Unit

        override fun updateTexImage() {
            updates++
        }
    }

    @Test
    fun mat4UniformIsAcceptedByTheUniformSet() {
        val matrix = FloatArray(16) { it.toFloat() }
        val uniforms = GraphicsShader.UniformSet().apply { set("uTransform", matrix) }

        assertSame(matrix, uniforms.entries["uTransform"])
        assertEquals(16, (uniforms.entries["uTransform"] as FloatArray).size)
    }

    @Test
    fun externalTextureStartsWithIdentityTransform() {
        val texture = FakeExternalTexture()

        val identity = floatArrayOf(
            1f, 0f, 0f, 0f,
            0f, 1f, 0f, 0f,
            0f, 0f, 1f, 0f,
            0f, 0f, 0f, 1f
        )
        assertArrayEquals(identity, texture.transform, 0f)
        assertTrue(texture.isExternal)

        // One region covering the whole texture, so a sprite can draw it.
        val region = texture.region
        assertSame(texture, region.texture)
        assertEquals(0f, region.u0, 0f)
        assertEquals(0f, region.v0, 0f)
        assertEquals(1f, region.u1, 0f)
        assertEquals(1f, region.v1, 0f)
    }

    @Test
    fun markFrameAvailableIsLatchedOnce() {
        val texture = FakeExternalTexture()

        texture.bind()
        assertEquals(0, texture.updates)

        texture.markFrameAvailable()
        texture.markFrameAvailable()
        texture.bind()
        assertEquals(1, texture.updates)

        // The flag was cleared by the first bind.
        texture.bind()
        assertEquals(1, texture.updates)

        texture.markFrameAvailable()
        texture.bind()
        assertEquals(2, texture.updates)
    }
}
