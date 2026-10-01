package com.example.ime

import android.view.inputmethod.ExtractedTextRequest
import android.view.inputmethod.InputConnection

data class ExtractedTextRange(
    val textToCorrect: String,
    val charsBeforeCursor: Int,
    val charsAfterCursor: Int,
    val isSelection: Boolean,
    val wholeField: Boolean = false
)

object TextExtractor {
    const val FALLBACK_LIMIT = 100_000

    /** Caller must block sensitive fields BEFORE calling this method.
     * Selection is read first and never causes a read of the rest of the editor.
     * Editors may return less than requested in the bounded fallback; Android
     * provides no reliable total length there. Never loop or silently accept a cap hit.
     */
    fun extract(inputConnection: InputConnection?): ExtractedTextRange? {
        val ic = inputConnection ?: return null
        val selected = ic.getSelectedText(0)
        if (!selected.isNullOrEmpty()) {
            return if (selected.isBlank()) null else ExtractedTextRange(selected.toString(), 0, 0, true)
        }
        val snapshot = ic.getExtractedText(ExtractedTextRequest().apply {
            hintMaxChars = FALLBACK_LIMIT
            hintMaxLines = FALLBACK_LIMIT
        }, 0)
        // An editor reporting a selection while getSelectedText failed cannot be
        // treated as an unselected field: abort rather than read beyond intention.
        if (snapshot != null && snapshot.selectionStart != snapshot.selectionEnd) return null
        val text = snapshot?.text?.toString()
        if (snapshot != null && text != null && snapshot.startOffset == 0 &&
            snapshot.partialStartOffset == -1 && snapshot.selectionStart == snapshot.selectionEnd &&
            snapshot.selectionStart in 0..text.length) {
            return if (text.isBlank()) null else ExtractedTextRange(text, snapshot.selectionStart,
                text.length - snapshot.selectionStart, false, wholeField = true)
        }
        val before = ic.getTextBeforeCursor(FALLBACK_LIMIT, 0)?.toString() ?: return null
        val after = ic.getTextAfterCursor(FALLBACK_LIMIT, 0)?.toString() ?: return null
        if (before.length >= FALLBACK_LIMIT || after.length >= FALLBACK_LIMIT) return null
        val combined = before + after
        return if (combined.isBlank()) null else ExtractedTextRange(combined, before.length, after.length, false)
    }
}
