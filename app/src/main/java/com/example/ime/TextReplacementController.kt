package com.example.ime

import android.view.inputmethod.InputConnection

object TextReplacementController {
    /** Revalidate the processed text so a delayed preview cannot erase newer edits.
     * Moving an unselected cursor is safe: fresh counts locate the same whole snapshot.
     * A selection stays private: revalidation reads only that selection.
     */
    fun replace(inputConnection: InputConnection?, extractedRange: ExtractedTextRange,
                correctedText: String): Boolean {
        val ic = inputConnection ?: return false
        ic.beginBatchEdit()
        try {
            val current = TextExtractor.extract(ic) ?: return false
            if (current.isSelection != extractedRange.isSelection ||
                current.textToCorrect != extractedRange.textToCorrect) return false
            if (current.isSelection) return ic.commitText(correctedText, 1)
            if (current.wholeField) {
                if (!ic.setSelection(0, current.textToCorrect.length)) return false
            } else {
                if (!ic.deleteSurroundingText(current.charsBeforeCursor, current.charsAfterCursor)) return false
            }
            return ic.commitText(correctedText, 1)
        } catch (_: Exception) {
            return false
        } finally {
            ic.endBatchEdit()
        }
    }
}
