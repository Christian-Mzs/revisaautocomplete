package com.example.suggestions

import android.view.inputmethod.InputConnection

/** Replaces only the letter sequence surrounding the cursor, leaving the rest of the field intact. */
object CurrentWordReplacement {
    fun replace(inputConnection: InputConnection?, expectedWord: String, replacement: String): Boolean {
        val ic = inputConnection ?: return false
        val before = ic.getTextBeforeCursor(CONTEXT_LIMIT, 0)?.toString() ?: return false
        val after = ic.getTextAfterCursor(CONTEXT_LIMIT, 0)?.toString() ?: return false
        var start = before.length
        while (start > 0 && isWordChar(before[start - 1])) start--
        var end = 0
        while (end < after.length && isWordChar(after[end])) end++
        val current = before.substring(start) + after.substring(0, end)
        if (current.isEmpty() || !current.equals(expectedWord, ignoreCase = true)) return false

        ic.beginBatchEdit()
        return try {
            ic.deleteSurroundingText(before.length - start, end)
            ic.commitText(replacement, 1)
        } finally {
            ic.endBatchEdit()
        }
    }

    private fun isWordChar(char: Char): Boolean = char.isLetter() ||
        Character.getType(char) == Character.NON_SPACING_MARK.toInt() ||
        Character.getType(char) == Character.COMBINING_SPACING_MARK.toInt()

    private const val CONTEXT_LIMIT = 80
}
