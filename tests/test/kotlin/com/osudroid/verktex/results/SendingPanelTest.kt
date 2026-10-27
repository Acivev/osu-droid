package com.osudroid.verktex.results

import com.osudroid.ui.SendingPanel
import com.osudroid.verktex.VerktexTestFixtures
import com.reco1l.verktex.BuiltIn
import com.reco1l.verktex.fonts.Font
import com.reco1l.verktex.fonts.FontFamily
import com.reco1l.verktex.graphics.GraphicsTexture
import com.reco1l.verktex.ui.UIComponent
import com.reco1l.verktex.ui.container.UIContainer
import com.reco1l.verktex.ui.text.UIText
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.After
import org.junit.Before
import org.junit.Test
import ru.nsu.ccfit.zuev.osu.Config

class SendingPanelTest {

    /** Every glyph is a 10 x 10 square, so texts can be measured on the JVM. */
    private class FakeFont(override val descriptor: Descriptor) : Font() {
        override val metrics = Metrics(ascent = 0f, descent = 0f, leading = 0f)
        override val isKerningSupported = false

        override fun onCreatePage(): GraphicsTexture = VerktexTestFixtures.FakeTexture(256f, 256f)

        override fun onCreateGlyph(codePoint: Int) = Glyph(
            codePoint = codePoint,
            advance = 10f,
            width = 10f,
            height = 10f,
            region = addGlyphSource(VerktexTestFixtures.FakeSource(10f, 10f)),
        )

        override fun getKerning(first: Int, second: Int) = 0f
    }

    private var resWidth = 0
    private var resHeight = 0
    private var savedFont: FontFamily? = null

    @After fun tearDown() {
        Config.setRES_WIDTH(resWidth)
        Config.setRES_HEIGHT(resHeight)
        savedFont?.let { BuiltIn.Fonts.Default = it }
    }

    @Before fun setUp() {
        VerktexTestFixtures.ensureInitialized()
        resWidth = Config.getRES_WIDTH()
        resHeight = Config.getRES_HEIGHT()
        Config.setRES_WIDTH(1280)
        Config.setRES_HEIGHT(720)
        savedFont = try { BuiltIn.Fonts.Default } catch (e: UninitializedPropertyAccessException) { null }
        BuiltIn.Fonts.Default = FontFamily("fake", object : FontFamily.Source {
            override fun onCreateFont(descriptor: Font.Descriptor): Font = FakeFont(descriptor)
        })
    }

    private fun UIComponent.texts(): List<String> = when (this) {
        is UIText -> listOf(text)
        is UIContainer -> toList().flatMap { it.texts() }
        else -> emptyList()
    }

    @Test fun scoreIsGroupedInThrees() {
        assertEquals("1 234 567", SendingPanel.formatScore(1234567))
        assertEquals("999", SendingPanel.formatScore(999))
        assertEquals("0", SendingPanel.formatScore(0))
    }

    @Test fun showAfterDetachDoesNothing() {
        val panel = SendingPanel(1, 1, 1f, 1f)

        // Detached, so neither call hops to the update thread nor changes the button.
        panel.show(2, 2, 2, 2f, 2f)
        panel.setFail()

        assertTrue("Sending..." in panel.texts())
        assertFalse("Dismiss" in panel.texts())
        assertFalse("Failed" in panel.texts())
    }
}
