package com.example.privacy

import android.text.InputType
import android.view.inputmethod.EditorInfo

object SensitiveFieldDetector {

    private const val TYPE_TEXT_VARIATION_CREDIT_CARD = 0x000000a0

    /**
     * Determines whether the given editor input is marked as a password, PIN,
     * credit card, or sensitive field where reading or transmitting text
     * must be strictly prohibited.
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
                InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD,
                TYPE_TEXT_VARIATION_CREDIT_CARD -> return true
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
}
