package com.example

import android.text.InputType
import android.view.inputmethod.EditorInfo
import com.example.privacy.SensitiveFieldDetector
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class SensitiveFieldDetectorTest {

    @Test
    fun `web edit text in browser is NOT sensitive`() {
        // TYPE_TEXT_VARIATION_WEB_EDIT_TEXT has constant value 0x000000a0 (160)
        val webEditTextInfo = EditorInfo().apply {
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_WEB_EDIT_TEXT
        }
        assertFalse(
            "Web edit text in Chrome/browser must not be flagged as sensitive",
            SensitiveFieldDetector.isSensitive(webEditTextInfo)
        )
    }

    @Test
    fun `normal text fields are NOT sensitive`() {
        val normalTextInfo = EditorInfo().apply {
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_NORMAL
        }
        assertFalse(SensitiveFieldDetector.isSensitive(normalTextInfo))

        val emailInfo = EditorInfo().apply {
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS
        }
        assertFalse(SensitiveFieldDetector.isSensitive(emailInfo))
    }

    @Test
    fun `password variations ARE sensitive`() {
        val passwordInfo = EditorInfo().apply {
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
        }
        assertTrue(SensitiveFieldDetector.isSensitive(passwordInfo))

        val webPasswordInfo = EditorInfo().apply {
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_WEB_PASSWORD
        }
        assertTrue(SensitiveFieldDetector.isSensitive(webPasswordInfo))

        val visiblePasswordInfo = EditorInfo().apply {
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD
        }
        assertTrue(SensitiveFieldDetector.isSensitive(visiblePasswordInfo))

        val pinInfo = EditorInfo().apply {
            inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_VARIATION_PASSWORD
        }
        assertTrue(SensitiveFieldDetector.isSensitive(pinInfo))
    }

    @Test
    fun `null editorInfo returns false`() {
        assertFalse(SensitiveFieldDetector.isSensitive(null))
    }

    @Test
    fun `word suggestions are suppressed for fields which disallow them`() {
        val ordinaryText = EditorInfo().apply { inputType = InputType.TYPE_CLASS_TEXT }
        assertTrue(SensitiveFieldDetector.allowsWordSuggestions(ordinaryText))

        val noSuggestions = EditorInfo().apply {
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS
        }
        assertFalse(SensitiveFieldDetector.allowsWordSuggestions(noSuggestions))

        val password = EditorInfo().apply {
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
        }
        assertFalse(SensitiveFieldDetector.allowsWordSuggestions(password))

        val numeric = EditorInfo().apply { inputType = InputType.TYPE_CLASS_NUMBER }
        assertFalse(SensitiveFieldDetector.allowsWordSuggestions(numeric))
        assertFalse(SensitiveFieldDetector.allowsWordSuggestions(null))
    }
}
