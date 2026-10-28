package com.osudroid.ui.verktex

import com.reco1l.verktex.graphics.GraphicsRenderer
import com.reco1l.verktex.ui.UICanvas

/**
 * A canvas drawing a triangle mesh in its local space with the component's color, every three
 * vertices forming a triangle. The blend mode, depth state and depth clear come from [UICanvas].
 */
class TriangleMeshCanvas : UICanvas() {

    private var vertices = FloatArray(0)

    /**
     * The number of vertices drawn, a multiple of 3.
     */
    var vertexCount = 0
        private set

    /**
     * Copies the first [vertexCount] local-space x, y pairs of [vertices] into the mesh, growing
     * its own array when needed. The canvas draws every frame, the copy shows on the next one.
     */
    fun setVertices(vertices: FloatArray, vertexCount: Int) {
        require(vertexCount % 3 == 0) { "The vertex count must be a multiple of 3, got $vertexCount." }
        require(vertices.size >= vertexCount * 2) { "Expected ${vertexCount * 2} floats, got ${vertices.size}." }

        if (this.vertices.size < vertexCount * 2) {
            this.vertices = FloatArray(vertexCount * 2)
        }

        vertices.copyInto(this.vertices, endIndex = vertexCount * 2)
        this.vertexCount = vertexCount
    }

    /**
     * Empties the mesh, nothing is drawn until [setVertices] is called again.
     */
    fun clear() {
        vertexCount = 0
    }

    override fun onDraw(renderer: GraphicsRenderer) {
        if (vertexCount > 0) {
            // The component alpha is applied through the renderer's color stack.
            renderer.drawTriangles(vertices, vertexCount, contentColor)
        }
    }
}
