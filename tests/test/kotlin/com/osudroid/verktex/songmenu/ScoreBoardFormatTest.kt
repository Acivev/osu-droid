package com.osudroid.verktex.songmenu

import org.junit.Assert.assertEquals
import org.junit.Test
import ru.nsu.ccfit.zuev.osu.menu.ScoreBoard

class ScoreBoardFormatTest {

    @Test fun scoreGetsASpaceBeforeEveryGroupOfThreeDigits() {
        assertEquals("0", ScoreBoard.formatScore(0))
        assertEquals("999", ScoreBoard.formatScore(999))
        assertEquals("1 000", ScoreBoard.formatScore(1000))
        assertEquals("12 345 678", ScoreBoard.formatScore(12345678))
        assertEquals("123 456", ScoreBoard.formatScore(123456))
    }

    @Test fun negativeScoreIsShownAbsolute() {
        assertEquals("1 234", ScoreBoard.formatScore(-1234))
    }

    @Test fun ppIsThePlainNumber() {
        assertEquals("1234", ScoreBoard.formatPP(1234))
    }
}
