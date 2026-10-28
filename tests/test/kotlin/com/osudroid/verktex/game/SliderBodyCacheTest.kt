package com.osudroid.verktex.game

import com.edlplan.framework.math.Vec2
import com.edlplan.framework.math.line.LinePath
import com.edlplan.osu.support.slider.SliderBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The precomputed triangles of `SliderBody.createCache`, which the body's three mesh layers draw.
 */
class SliderBodyCacheTest {

    private fun path(vararg points: Pair<Float, Float>) = LinePath().apply {
        points.forEach { (x, y) -> add(Vec2(x, y)) }
    }

    private fun cache(path: LinePath) = SliderBody.createCache(path, 54f, 6f, true, 20f, true)

    private fun assertTriangles(layer: SliderBody.RenderPathCache.ComponentCache) {
        assertTrue(layer.hasCache)
        assertTrue(layer.vertexLength > 0)
        // Two floats per vertex, three vertices per triangle.
        assertEquals(0, layer.vertexLength % 6)
    }

    @Test fun straightPathProducesTriangles() {
        val cache = cache(path(0f to 0f, 200f to 0f))

        assertTriangles(cache.background)
        assertTriangles(cache.border)
        assertTriangles(cache.hint)
    }

    @Test fun curvedPathHasMoreVertices() {
        val straight = cache(path(0f to 0f, 100f to 0f, 200f to 0f))
        val curved = cache(path(0f to 0f, 60f to 40f, 100f to 60f, 140f to 40f, 200f to 0f))

        assertTrue(curved.background.vertexLength > straight.background.vertexLength)
        assertTrue(curved.border.vertexLength > straight.border.vertexLength)
    }
}
