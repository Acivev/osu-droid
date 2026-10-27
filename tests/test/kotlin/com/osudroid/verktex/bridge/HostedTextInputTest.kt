package com.osudroid.verktex.bridge

import android.app.Activity
import android.os.Looper
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.widget.AppCompatEditText
import com.reco1l.verktex.ui.ITextInput
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf

@RunWith(RobolectricTestRunner::class)
class HostedTextInputTest {
    private lateinit var activity: Activity

    @Before
    fun setUp() {
        activity = Robolectric.buildActivity(Activity::class.java).setup().get()
    }

    private fun idle() = shadowOf(Looper.getMainLooper()).idle()

    private fun editTexts(): List<View> {
        val decor = activity.window.decorView as ViewGroup
        return (0 until decor.childCount).map { decor.getChildAt(it) }.filterIsInstance<AppCompatEditText>()
    }

    @Test
    fun detachRemovesTheEditTextFromTheWindow() {
        val input = HostedTextInput(activity)
        input.attach(FakeTextInput()); idle()
        assertTrue(editTexts().isNotEmpty())
        input.detach(); idle()
        assertFalse(editTexts().isNotEmpty())
    }

    @Test
    fun attachAfterDetachAddsItAgainWithFocus() {
        val input = HostedTextInput(activity)
        input.attach(FakeTextInput()); idle()
        input.detach(); idle()
        input.attach(FakeTextInput()); idle()
        assertEquals(1, editTexts().size)
        assertTrue(editTexts().first().hasFocus())
    }
}

private class FakeTextInput : ITextInput {
    override var value: String = ""
    override val maxCharacters: Int = 0
    override fun isCharacterAllowed(char: String) = true
    override fun isTextValid(text: String) = true
    override fun notifyInputError() {}
}
