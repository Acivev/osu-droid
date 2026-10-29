package com.osudroid.verktex.game

import com.osudroid.verktex.VerktexTestFixtures
import com.reco1l.verktex.texture.Textures
import com.reco1l.verktex.ui.container.UIContainer
import com.reco1l.verktex.ui.shape.UIBox
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import ru.nsu.ccfit.zuev.osu.Config
import ru.nsu.ccfit.zuev.osu.game.BreakAnimator
import java.io.File

/**
 * The break fades the Verktex dim box: its alpha is `(1 - brightness) * (1 - percent)` during the fade,
 * `1 - brightness` once resumed, and untouched while the no-change-in-breaks setting is on.
 */
@RunWith(RobolectricTestRunner::class)
class BreakDimTest {

    // The setting is read from a static field that only loadConfig sets.
    private val noChangeField = Config::class.java.getDeclaredField("noChangeDimInBreaks").apply { isAccessible = true }

    // The fade step is private, the update loop calls it at the break's fade times.
    private val setBgFade = BreakAnimator::class.java.getDeclaredMethod("setBgFade", Float::class.javaPrimitiveType)
        .apply { isAccessible = true }

    private var previousSkinPath: String? = null
    private var previousNoChange = false

    private lateinit var animator: BreakAnimator
    private val dim = UIBox()

    @Before fun setUp() {
        // The skin directory does not exist, the warning arrow comes from the assets.
        previousSkinPath = Config.getSkinPath()
        Config.setSkinPath(File("no-such-skin").absolutePath + "/")
        previousNoChange = noChangeField.getBoolean(null)
        noChangeField.setBoolean(null, false)

        val provider = VerktexTestFixtures.ensureInitialized()
        provider.textureSizes["gfx/play-warningarrow.png"] = 32f to 32f

        animator = BreakAnimator(UIContainer(), null, null)
        animator.setDimRectangle(dim)
        animator.setDimBrightness(0.25f)
        dim.alpha = 0.75f
    }

    @After fun tearDown() {
        Textures.unloadAll()
        Config.setSkinPath(previousSkinPath)
        noChangeField.setBoolean(null, previousNoChange)
    }

    @Test fun fadeScalesTheDimByTheBreakProgress() {
        setBgFade.invoke(animator, 0.5f)

        assertEquals(0.375f, dim.alpha, EPSILON)
    }

    @Test fun resumeRestoresTheDim() {
        setBgFade.invoke(animator, 1f)
        assertEquals(0f, dim.alpha, EPSILON)

        animator.reset()

        assertEquals(0.75f, dim.alpha, EPSILON)
    }

    @Test fun noChangeInBreaksLeavesTheDim() {
        noChangeField.setBoolean(null, true)

        setBgFade.invoke(animator, 0.5f)
        assertEquals(0.75f, dim.alpha, EPSILON)

        animator.reset()
        assertEquals(0.75f, dim.alpha, EPSILON)
    }

    private companion object {
        const val EPSILON = 1e-5f
    }
}
