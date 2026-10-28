package com.osudroid.verktex.game

import com.osudroid.verktex.VerktexTestFixtures
import com.reco1l.verktex.data.LayoutConstraints
import com.reco1l.verktex.data.Vec4
import com.reco1l.verktex.texture.Textures
import com.reco1l.verktex.ui.container.UIContainer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import ru.nsu.ccfit.zuev.osu.Config
import ru.nsu.ccfit.zuev.osu.game.cursor.main.CursorEntity
import java.io.File

/**
 * The raw pointer's fast path runs on the draw thread, after the layout: the cursor reaches the raw position
 * in that draw through its translation, and the next position from the update thread replaces it.
 */
@RunWith(RobolectricTestRunner::class)
class GameplayFastPathTest {

    private val window = Vec4(0f, 0f, 1280f, 720f)

    private lateinit var parent: UIContainer
    private lateinit var cursor: CursorEntity
    private var previousSkinPath: String? = null

    @Before fun setUp() {
        // The skin directory does not exist, the cursor comes from the assets.
        previousSkinPath = Config.getSkinPath()
        Config.setSkinPath(File("no-such-skin").absolutePath + "/")

        val provider = VerktexTestFixtures.ensureInitialized()
        provider.textureSizes["gfx/cursor.png"] = 64f to 64f

        cursor = CursorEntity()
        parent = UIContainer()
        parent += cursor
    }

    @After fun tearDown() {
        Textures.unloadAll()
        Config.setSkinPath(previousSkinPath)
    }

    private fun layout() {
        parent.measure(LayoutConstraints(0f, 1280f, 0f, 720f))
        parent.layout(window)
    }

    /** Where the cursor's origin is drawn in its parent, with the transformations a draw computes. */
    private fun drawnPosition(): Pair<Float, Float> {
        cursor.calculateTransformations()
        val point = cursor.toParentSpace(0f, 0f)
        return point.x to point.y
    }

    @Test fun fastPathPlacesTheCursorWithoutALayout() {
        cursor.setPosition(100f, 200f)
        layout()

        cursor.setDrawPosition(300f, 400f)

        assertEquals(300f to 400f, drawnPosition())
    }

    @Test fun updatePathPositionReplacesTheFastPath() {
        cursor.setPosition(100f, 200f)
        layout()
        cursor.setDrawPosition(300f, 400f)

        cursor.setPosition(150f, 250f)
        layout()

        assertEquals(0f, cursor.translationX.resolve(0f))
        assertEquals(0f, cursor.translationY.resolve(0f))
        assertEquals(150f to 250f, drawnPosition())
    }
}
