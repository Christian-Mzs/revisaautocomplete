package com.example.ime

import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputConnection
import com.example.privacy.SensitiveFieldDetector

data class CurrentWord(val beforeCursor: String, val afterCursor: String) {
    val text: String get() = beforeCursor + afterCursor
}

object CurrentWordExtractor {
    const val CONTEXT_LIMIT = 48

    fun extract(connection: InputConnection?, editorInfo: EditorInfo?): CurrentWord? {
        // Fail closed before ANY editor read, including selection reads.
        if (connection == null || editorInfo == null || SensitiveFieldDetector.isSensitive(editorInfo)) return null
        if (!connection.getSelectedText(0).isNullOrEmpty()) return null
        val before = connection.getTextBeforeCursor(CONTEXT_LIMIT, 0)?.toString() ?: return null
        val after = connection.getTextAfterCursor(CONTEXT_LIMIT, 0)?.toString() ?: return null
        val left = before.takeLastWhile(::isWordCharacter)
        val right = after.takeWhile(::isWordCharacter)
        // Do not offer the previous word after a delimiter, or a truncated word.
        if (left.isEmpty() || left.length == CONTEXT_LIMIT || right.length == CONTEXT_LIMIT) return null
        return CurrentWord(left, right)
    }

    private fun isWordCharacter(char: Char): Boolean =
        char.isLetter() || Character.getType(char) == Character.NON_SPACING_MARK.toInt()
}
