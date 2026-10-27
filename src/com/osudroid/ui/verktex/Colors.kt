package com.osudroid.ui.verktex

import com.reco1l.verktex.data.Color4

/**
 * Multiplies the RGB components and keeps the alpha, like the AndEngine `Color4 * Float`.
 */
operator fun Color4.times(scalar: Float) = Color4(red * scalar, green * scalar, blue * scalar, alpha)

/**
 * The same colour as a Verktex [Color4].
 */
fun com.reco1l.framework.Color4.toVerktex() = Color4(red, green, blue, alpha)

/**
 * Multiplies the components, like AndEngine multiplied a child's colour by its ancestors' when drawing.
 */
operator fun Color4.times(other: Color4) = Color4(red * other.red, green * other.green, blue * other.blue, alpha * other.alpha)
