package com.osudroid.ui.verktex

import com.reco1l.verktex.Verktex
import com.reco1l.verktex.data.LayoutConstraints
import com.reco1l.verktex.data.Vec2
import com.reco1l.verktex.data.Vec4
import com.reco1l.verktex.data.fill
import com.reco1l.verktex.ui.container.UIContainer

/**
 * A container that covers the whole window when it is added as a Verktex overlay, like the AndEngine
 * camera HUD did.
 *
 * The overlay scene pads its children by the display cutout's safe area and clamps their size to what
 * is left. This container lays its children out over the window instead and takes presses in the
 * cutout margins as well. Verktex has no open hit-test method (`containsScenePoint` and
 * `toLocalSpaceFromScene` are final), so the hit area is widened through `sceneToLocalMatrix`.
 */
open class FullWindowContainer : UIContainer() {

    init {
        width = fill()
        height = fill()
    }

    private val windowWidth
        get() = Verktex.graphics.window.windowWidth.toFloat()

    private val windowHeight
        get() = Verktex.graphics.window.windowHeight.toFloat()

    override fun onMeasure(parentConstraints: LayoutConstraints, contentConstraints: LayoutConstraints): Vec2 {
        val window = LayoutConstraints(0f, windowWidth, 0f, windowHeight)
        forEach { it.measure(window) }
        return contentConstraints.constrain(Vec2(windowWidth, windowHeight))
    }

    override fun layout(assignedBounds: Vec4) {
        super.layout(Vec4(0f, 0f, windowWidth, windowHeight))
    }

    override fun onLayout(bounds: Vec4) {
        val window = Vec4(0f, 0f, windowWidth, windowHeight)
        forEach { it.layout(window) }
    }

    override fun calculateTransformations() {
        super.calculateTransformations()

        // The container sits at the window origin. Its bounds are narrower than the window, so the hit
        // test (containsScenePoint) maps the whole window onto them; drawing and the children keep using
        // localToSceneMatrix.
        if (windowWidth > 0f && windowHeight > 0f) {
            sceneToLocalMatrix.identity()
            sceneToLocalMatrix.scale(bounds.width / windowWidth, bounds.height / windowHeight)
        }
    }
}
