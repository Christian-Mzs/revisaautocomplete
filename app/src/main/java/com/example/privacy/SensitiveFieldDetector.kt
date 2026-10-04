package com.example.privacy

import android.text.InputType
import android.view.inputmethod.EditorInfo

object SensitiveFieldDetector {

    /**
     * Determines whether the given editor input is marked as a password, PIN,
     * or sensitive field where reading or transmitting text must be strictly prohibited.
     */
    fun isSensitive(editorInfo: EditorInfo?): Boolean {
        if (editorInfo == null) return false

        val inputType = editorInfo.inputType
        val inputClass = inputType and InputType.TYPE_MASK_CLASS
        val variation = inputType and InputType.TYPE_MASK_VARIATION

        // Text passwords and visible passwords
        if (inputClass == InputType.TYPE_CLASS_TEXT) {
            when (variation) {
                InputType.TYPE_TEXT_VARIATION_PASSWORD,
                InputType.TYPE_TEXT_VARIATION_WEB_PASSWORD,
                InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD -> return true
            }
        }

        // Numeric PINs / passwords
        if (inputClass == InputType.TYPE_CLASS_NUMBER) {
            if (variation == InputType.TYPE_NUMBER_VARIATION_PASSWORD) {
                return true
            }
        }

        return false
    }

    /** True only for ordinary text editors where the app explicitly permits suggestions. */
    fun allowsWordSuggestions(editorInfo: EditorInfo?): Boolean {
        if (editorInfo == null || isSensitive(editorInfo)) return false
        val inputType = editorInfo.inputType
        if ((inputType and InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS) != 0) return false
        if ((inputType and InputType.TYPE_MASK_CLASS) != InputType.TYPE_CLASS_TEXT) return false
        val variation = inputType and InputType.TYPE_MASK_VARIATION
        return variation != InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS &&
            variation != InputType.TYPE_TEXT_VARIATION_WEB_EMAIL_ADDRESS &&
            variation != InputType.TYPE_TEXT_VARIATION_URI &&
            variation != InputType.TYPE_TEXT_VARIATION_FILTER
    }
}
