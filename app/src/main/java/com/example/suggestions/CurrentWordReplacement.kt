package com.example.suggestions

import android.view.inputmethod.InputConnection

/** Replaces the word tracked from Revisa key events, without reading editor text. */
object CurrentWordReplacement {
    fun replace(inputConnection: InputConnection?, expectedWord: String, replacement: String): Boolean {
        val ic = inputConnection ?: return false
        if (expectedWord.isEmpty() || replacement.isEmpty()) return false

        ic.beginBatchEdit()
        return try {
            if (!ic.deleteSurroundingText(expectedWord.length, 0)) return false
            ic.commitText(replacement, 1)
        } finally {
            ic.endBatchEdit()
        }
    }
}

