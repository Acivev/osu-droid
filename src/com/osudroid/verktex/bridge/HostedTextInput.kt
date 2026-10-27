package com.osudroid.verktex.bridge

import android.app.Activity
import android.text.InputFilter
import android.text.InputType
import android.text.TextWatcher
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.TextView.BufferType
import androidx.appcompat.widget.AppCompatEditText
import androidx.core.content.getSystemService
import androidx.core.widget.doOnTextChanged
import com.reco1l.verktex.Verktex
import com.reco1l.verktex.ui.ITextInput

/**
 * Soft keyboard input for Verktex text inputs while Verktex is hosted, copied from
 * `AndroidGameHost` because the host activity cannot extend it.
 */
class HostedTextInput(private val activity: Activity) {

    // Using a hidden EditText is the only way to properly handle soft keyboard
    // input in Android allowing UTF-16 characters, selection and clipboard operations.
    private val editTextView by lazy { AppCompatEditText(activity).apply {
        layoutParams = ViewGroup.LayoutParams(1, 1)
        alpha = 0f

        inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS
        imeOptions = EditorInfo.IME_ACTION_DONE or EditorInfo.IME_FLAG_NO_FULLSCREEN or EditorInfo.IME_FLAG_NO_EXTRACT_UI

        isClickable = false
    } }

    private var currentTextWatcher: TextWatcher? = null


    fun attach(textInput: ITextInput) = activity.runOnUiThread {
        if (currentTextWatcher != null)
            editTextView.removeTextChangedListener(currentTextWatcher)

        // The view must be part of the hierarchy to receive focus and IME input.
        if (editTextView.parent == null) {
            (activity.window.decorView as ViewGroup).addView(editTextView)
        }

        editTextView.setText(textInput.value, BufferType.EDITABLE)
        editTextView.setSelection(editTextView.length())

        editTextView.setOnEditorActionListener { _, _, _ ->
            Verktex.withTreeLock { textInput.onImeAction() }
            true
        }

        // Rejected input is filtered before it reaches the text, reverting it afterwards would break
        // the IME's composing state.
        editTextView.filters = arrayOf(InputFilter { source, sourceStart, sourceEnd, dest, destStart, destEnd ->
            val inserted = source.subSequence(sourceStart, sourceEnd).toString()
            val result = dest.substring(0, destStart) + inserted + dest.substring(destEnd)

            if (isTextAccepted(textInput, inserted, result)) {
                null
            } else {
                Verktex.withTreeLock { textInput.notifyInputError() }
                // Keeps the replaced range as it was.
                dest.subSequence(destStart, destEnd)
            }
        })

        currentTextWatcher = editTextView.doOnTextChanged { text, _, _, _ ->
            // The text watcher runs on the UI thread, while the component tree belongs to the engine workers.
            val newText = text?.toString() ?: ""
            Verktex.withTreeLock { textInput.value = newText }
        }

        editTextView.requestFocus()
        activity.getSystemService<InputMethodManager>()?.showSoftInput(editTextView, InputMethodManager.SHOW_IMPLICIT)
    }

    private fun isTextAccepted(textInput: ITextInput, inserted: String, result: String): Boolean {
        if (textInput.maxCharacters > 0 && result.codePointCount(0, result.length) > textInput.maxCharacters) {
            return false
        }

        var index = 0
        while (index < inserted.length) {
            val charCount = Character.charCount(inserted.codePointAt(index))
            if (!textInput.isCharacterAllowed(inserted.substring(index, index + charCount))) {
                return false
            }
            index += charCount
        }

        return textInput.isTextValid(result)
    }

    fun detach() = activity.runOnUiThread {
        if (currentTextWatcher != null)
            editTextView.removeTextChangedListener(currentTextWatcher)

        currentTextWatcher = null
        editTextView.setOnEditorActionListener(null)
        editTextView.filters = arrayOf()

        activity.getSystemService<InputMethodManager>()?.hideSoftInputFromWindow(editTextView.windowToken, 0)
        editTextView.clearFocus()
        (editTextView.parent as? ViewGroup)?.removeView(editTextView)
    }
}
