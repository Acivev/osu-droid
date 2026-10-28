package com.osudroid.verktex.game

import org.junit.Assert.assertTrue
import org.junit.Test
import ru.nsu.ccfit.zuev.osu.game.GameScene
import ru.nsu.ccfit.zuev.osu.scoring.ScoreNumber

/**
 * The skin textures the playfield looks up are loaded while the game loads, so no texture is decoded
 * during play. The expected names are the string literals of the texture lookups in the playfield
 * files (hit objects, effects, cursors, flashlight, countdown, combo burst, break) and the judgement
 * names `GameScene` passes to its effects.
 */
class GameplayTexturesTest {

    private val expected = listOf(
        // Judgements and their lighting.
        "hit0", "hit50", "hit100", "hit100k", "hit300", "hit300k", "hit300g", "sliderpoint10", "sliderpoint30", "lighting",
        // Circles and sliders.
        "approachcircle", "defaultapproachcircle", "hitcircle", "hitcircleoverlay",
        "sliderstartcircle", "sliderstartcircleoverlay", "sliderendcircle", "sliderendcircleoverlay",
        "reversearrow", "sliderb", "sliderfollowcircle", "sliderscorepoint", "followpoint",
        // Spinners.
        "spinner-background", "spinner-circle", "spinner-metre", "spinner-approachcircle", "spinner-spin", "spinner-clear",
        "spinner-middle", "spinner-middle2", "spinner-bottom", "spinner-top", "spinner-glow",
        // Cursors and flashlight.
        "cursor", "cursormiddle", "cursortrail", "flashlight_cursor",
        // Combo burst.
        "comboburst", "comboburst-0", "comboburst-1", "comboburst-2", "comboburst-3", "comboburst-4",
        "comboburst-5", "comboburst-6", "comboburst-7", "comboburst-8", "comboburst-9",
        // Countdown.
        "count1", "count2", "count3", "go", "ready",
        // Break with its mark, unranked mark and skip button.
        "play-warningarrow", "section-pass", "section-fail", "play-unranked", "play-skip",
        "ranking-XH-small", "ranking-SH-small", "ranking-X-small", "ranking-S-small", "ranking-A-small",
        "ranking-B-small", "ranking-C-small", "ranking-D-small"
    )

    @Test fun preloadListCoversTheLookups() {
        val list = GameScene.GAMEPLAY_TEXTURES.toSet()
        val missing = expected.filterNot { it in list }

        assertTrue("Missing from the preload list: $missing", missing.isEmpty())
    }

    /** The names `SpriteFont` (hit circle numbers) and `ScoreNumber` look up after the skin's prefix. */
    private val expectedDigits = listOf("0", "1", "2", "3", "4", "5", "6", "7", "8", "9", "comma", "percent", "x", "d", "p")

    @Test fun digitListCoversTheLookups() {
        val list = GameScene.GAMEPLAY_DIGITS.toSet()
        val scoreNames = "0123456789.,%x".map { ScoreNumber.textureNameFor(it) }
        val missing = (expectedDigits + scoreNames).distinct().filterNot { it in list }

        assertTrue("Missing from the digit list: $missing", missing.isEmpty())
    }
}
