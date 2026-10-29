package com.osudroid.verktex.game

import org.junit.Assert.assertEquals
import org.junit.Test
import ru.nsu.ccfit.zuev.osu.game.GameScene
import ru.nsu.ccfit.zuev.osu.game.GameScene.BackgroundSource

/**
 * The gameplay background follows the AndEngine slot rules: the safe-background setting and a beatmap
 * without a background file show the menu background, a file that decodes shows itself, a file that does
 * not decode shows the menu background the slot stored, and a slot never filled shows the colour box. The
 * picture is centred and scaled to the height or the width of the virtual resolution.
 */
class BackgroundChoiceTest {

    @Test fun safeBackgroundWinsOverTheBeatmapFile() {
        assertEquals(BackgroundSource.MenuBackground, GameScene.chooseBackground(true, "bg.jpg", true, true))
    }

    @Test fun noFilenameShowsTheMenuBackground() {
        assertEquals(BackgroundSource.MenuBackground, GameScene.chooseBackground(false, null, true, true))
    }

    @Test fun decodedFileShowsTheBeatmapBackground() {
        assertEquals(BackgroundSource.BeatmapBackground, GameScene.chooseBackground(false, "bg.jpg", true, true))
    }

    @Test fun undecodableFileFallsBackToTheMenuBackground() {
        assertEquals(BackgroundSource.MenuBackground, GameScene.chooseBackground(false, "bg.jpg", true, false))
    }

    @Test fun anUnfilledSlotShowsTheColourBox() {
        assertEquals(BackgroundSource.Colour, GameScene.chooseBackground(false, "bg.jpg", false, false))
    }

    @Test fun placementKeepsTheHeight() {
        val out = FloatArray(3)
        GameScene.backgroundPlacement(true, 1920f, 1080f, 1280f, 720f, out)

        assertEquals(0.6666667f, out[0], EPSILON)
        assertEquals(-320f, out[1], EPSILON)
        assertEquals(-180f, out[2], EPSILON)
    }

    @Test fun placementKeepsTheWidth() {
        val out = FloatArray(3)
        GameScene.backgroundPlacement(false, 1920f, 1080f, 1280f, 720f, out)
        assertEquals(0.6666667f, out[0], EPSILON)

        GameScene.backgroundPlacement(false, 1024f, 768f, 1280f, 720f, out)
        assertEquals(1.25f, out[0], EPSILON)
        assertEquals(128f, out[1], EPSILON)
        assertEquals(-24f, out[2], EPSILON)
    }

    private companion object {
        const val EPSILON = 1e-5f
    }
}
