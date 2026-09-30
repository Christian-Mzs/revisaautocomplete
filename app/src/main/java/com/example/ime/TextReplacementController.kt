package com.example.ime

import android.view.inputmethod.InputConnection

object TextReplacementController {

    /**
     * Replaces the extracted sentence with the corrected text, ensuring surrounding
     * text is preserved and cursor position is correctly updated.
     */
    fun replace(
        inputConnection: InputConnection?,
        extractedSentence: ExtractedSentence,
        correctedText: String
    ): Boolean {
        if (inputConnection == null) return false

        return if (extractedSentence.isSelection) {
            // Replaces selected text and positions cursor at the end
            inputConnection.commitText(correctedText, 1)
        } else {
            inputConnection.beginBatchEdit()
            try {
                // Delete only the characters captured before and after cursor
                val beforeCount = extractedSentence.charsBeforeCursor
                val afterCount = extractedSentence.charsAfterCursor

                inputConnection.deleteSurroundingText(beforeCount, afterCount)
                // Commit corrected text and place cursor right after it
                inputConnection.commitText(correctedText, 1)
                true
            } catch (e: Exception) {
                false
            } finally {
                inputConnection.endBatchEdit()
            }
        }
    }
}
