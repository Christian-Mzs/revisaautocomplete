package com.example.ime

import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputConnection

object CurrentWordReplacement {
    fun replace(connection: InputConnection?, editorInfo: EditorInfo?, expected: CurrentWord, candidate: String): Boolean {
        // Recheck sensitivity, selection and word at the time of the tap.
        if (candidate !in LocalSuggestionEngine.suggest(expected.text)) return false
        val current = CurrentWordExtractor.extract(connection, editorInfo) ?: return false
        if (current != expected) return false
        val ic = connection ?: return false
        ic.beginBatchEdit()
        return try {
            if (!ic.deleteSurroundingText(current.beforeCursor.length, current.afterCursor.length)) false
            else ic.commitText(candidate, 1)
        } finally {
            ic.endBatchEdit()
        }
    }
}
