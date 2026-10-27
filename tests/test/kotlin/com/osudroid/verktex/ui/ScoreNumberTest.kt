package com.osudroid.verktex.ui

import com.osudroid.ui.verktex.ScoreNumber
import com.osudroid.verktex.VerktexTestFixtures
import com.osudroid.verktex.VerktexTestFixtures.FakeSource
import com.osudroid.verktex.VerktexTestFixtures.FakeTexture
import com.reco1l.verktex.data.Dimension
import com.reco1l.verktex.graphics.GraphicsTexture
import com.reco1l.verktex.ui.UIComponent
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

class ScoreNumberTest {

    @Before fun setUp() {
        VerktexTestFixtures.ensureInitialized()
    }

    private fun fakeRegion(width: Float, height: Float): GraphicsTexture.Region =
        FakeTexture(64f, 64f).addSource(FakeSource(width, height))!!

    private fun Dimension.pixels() = (this as Dimension.Fixed).toPixels()

    private fun ScoreNumber.letters(): List<UIComponent> = toList()

    private fun assertEquals(expected: List<Float>, actual: List<Float>, delta: Float) {
        assertEquals(expected.size, actual.size)
        expected.zip(actual).forEach { (e, a) -> assertEquals(e, a, delta) }
    }


    @Test fun mapsCharactersLikeTheLegacyNumber() {
        assertEquals("7", ScoreNumber.textureNameFor('7'))
        assertEquals("comma", ScoreNumber.textureNameFor('.'))
        assertEquals("comma", ScoreNumber.textureNameFor(','))
        assertEquals("percent", ScoreNumber.textureNameFor('%'))
        assertEquals("x", ScoreNumber.textureNameFor('x'))
        assertEquals("x", ScoreNumber.textureNameFor('-'))
    }

    @Test fun laysLettersOutLikeTheLegacyNumber() {
        val number = ScoreNumber(0f, 0f, "12x", 1.1f, center = false) { fakeRegion(10f, 20f) }
        val letters = number.letters()

        assertEquals(3, letters.size)
        assertEquals(listOf(0f, 13.31f, 26.62f), letters.map { it.x.pixels() }, 0.01f)
        assertEquals(11f, letters[0].width.pixels(), 0.01f)
        assertEquals(24.2f, number.textHeight, 0.01f)
    }

    @Test fun emptyTextHasNoHeightAndNoChildren() {
        val number = ScoreNumber(0f, 0f, "", 1f, center = false) { fakeRegion(10f, 20f) }

        assertEquals(0, number.letters().size)
        assertEquals(0f, number.textHeight, 0f)
    }

    @Test fun centeredTextIsShiftedByHalfItsWidth() {
        val number = ScoreNumber(0f, 0f, "12", 1.1f, center = true) { fakeRegion(10f, 20f) }

        assertEquals(listOf(-11f, 2.31f), number.letters().map { it.x.pixels() }, 0.01f)
    }

    @Test fun scaleOneHasNoGaps() {
        val number = ScoreNumber(0f, 0f, "12", 1f, center = false) { fakeRegion(10f, 20f) }
        val letters = number.letters()

        assertEquals(listOf(0f, 10f), letters.map { it.x.pixels() }, 0.01f)
        assertEquals(10f, letters[0].width.pixels(), 0.01f)
        assertEquals(20f, number.textHeight, 0.01f)
    }

    @Test fun setTextReplacesTheLetters() {
        val number = ScoreNumber(0f, 0f, "123", 1f, center = false) { fakeRegion(10f, 20f) }
        number.setText("4")

        assertEquals(1, number.letters().size)
    }
}
