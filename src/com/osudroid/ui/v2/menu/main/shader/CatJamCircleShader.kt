package com.osudroid.ui.v2.menu.main.shader

import com.reco1l.verktex.Platform
import com.reco1l.verktex.Verktex
import com.reco1l.verktex.data.Vec4
import com.reco1l.verktex.graphics.GraphicsShader
import com.reco1l.verktex.graphics.GraphicsShader.UniformSet
import com.reco1l.verktex.graphics.GraphicsTexture

/**
 * Single-texture GLSL shader for [com.osudroid.ui.v2.menu.main.KiaiCatJamSprite].
 *
 * The GLSL lives in the assets: Verktex's `verktex/shaders/main.vert` as the vertex shader and
 * `shaders/catjam_circle.frag` as the fragment shader. The frame's atlas sub-region UVs are passed as
 * [UNIFORM_FRAME_RECT], so the fragment shader can map them back to a normalized 0-to-1 position
 * across the frame regardless of the texture atlas region.
 *
 * The fragment shader uses that position to compute the distance from the frame
 * center (0.5, 0.5) and smoothly discards pixels that fall outside the circular logo area,
 * clipping the animation to a circle without requiring any mask texture.
 *
 * One instance per sprite, so the shader is created on the renderer that sprite is drawn with.
 */
class CatJamCircleShader {

    /** Created by [create], null before that or when the shader source is missing. */
    var shader: GraphicsShader? = null; private set

    /** Whether the shader was created already, a missing source is not retried every frame. */
    private var isShaderRequested = false

    /** Creates the shader once. Called on the update thread, its GL program is built lazily when first bound. */
    fun create() {
        if (isShaderRequested) return
        isShaderRequested = true
        shader = Platform.provider.createShaderSource(VERTEX_SHADER, FRAGMENT_SHADER, false)
            ?.let(Verktex.graphics::createShader)
    }

    /** The uniforms for drawing [region] clipped to a circle of [radius] in frame space. */
    fun uniformSet(region: GraphicsTexture.Region, radius: Float) = UniformSet()
        .set(UNIFORM_FRAME_RECT, Vec4(region.u0, region.v0, region.u1, region.v1))
        .set(UNIFORM_RADIUS, radius)

    companion object {
        /** UVs of the current frame within the sheet: u0, v0, u1, v1. */
        const val UNIFORM_FRAME_RECT = "uFrameRect"

        /** Circle radius in frame space (0.5 = fills the whole frame). */
        const val UNIFORM_RADIUS = "uRadius"

        private const val VERTEX_SHADER = "verktex/shaders/main.vert"

        // A small smooth edge avoids aliasing at the circle rim.
        private const val FRAGMENT_SHADER = "shaders/catjam_circle.frag"
    }
}
