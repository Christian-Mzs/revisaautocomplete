package com.example.ime

import android.view.inputmethod.InputConnection

data class ExtractedSentence(
    val textToCorrect: String,
    val charsBeforeCursor: Int,
    val charsAfterCursor: Int,
    val isSelection: Boolean
)

object SentenceExtractor {

    const val DEFAULT_MAX_LOOKBACK = 300
    const val DEFAULT_MAX_LOOKAHEAD = 150
    val TERMINATORS = charArrayOf('.', '!', '?', '\n')

    /**
     * Safely extracts the current sentence around cursor or selected text
     * without reading unnecessary conversational context.
     */
    fun extract(
        inputConnection: InputConnection?,
        maxLookback: Int = DEFAULT_MAX_LOOKBACK,
        maxLookahead: Int = DEFAULT_MAX_LOOKAHEAD
    ): ExtractedSentence? {
        if (inputConnection == null) return null

        // 1. Priority: If user has explicitly selected text, correct only that selection
        val selectedText = inputConnection.getSelectedText(0)
        if (!selectedText.isNullOrBlank()) {
            val selectionString = selectedText.toString()
            return ExtractedSentence(
                textToCorrect = selectionString.trim(),
                charsBeforeCursor = 0,
                charsAfterCursor = 0,
                isSelection = true
            )
        }

        // 2. No selection: Read up to maxLookback before cursor and maxLookahead after cursor
        val beforeCs = inputConnection.getTextBeforeCursor(maxLookback, 0)
        val afterCs = inputConnection.getTextAfterCursor(maxLookahead, 0)

        val before = beforeCs?.toString().orEmpty()
        val after = afterCs?.toString().orEmpty()

        if (before.isEmpty() && after.isEmpty()) {
            return null
        }

        // Analyze 'before'
        // If the user typed a terminator right at the end (e.g., "eu vou."),
        // that terminator marks the end of the current sentence, not a previous one.
        val trimmedBefore = before.trimEnd()
        val hasTrailingTerminator = trimmedBefore.isNotEmpty() &&
                TERMINATORS.contains(trimmedBefore.last())

        val searchLimitIndex = if (hasTrailingTerminator) {
            // Search before this trailing terminator
            trimmedBefore.length - 1
        } else {
            before.length
        }

        val lastTerminatorBefore = before.substring(0, searchLimitIndex).indexOfLast { TERMINATORS.contains(it) }

        val sentenceStartInBefore = if (lastTerminatorBefore >= 0) {
            // Skip any spaces immediately following the terminator
            var idx = lastTerminatorBefore + 1
            while (idx < before.length && (before[idx] == ' ' || before[idx] == '\t')) {
                idx++
            }
            idx
        } else {
            // No previous terminator, entire 'before' belongs to the sentence (or paragraph)
            var idx = 0
            while (idx < before.length && (before[idx] == ' ' || before[idx] == '\t')) {
                idx++
            }
            idx
        }

        val relevantBefore = before.substring(sentenceStartInBefore)

        // Analyze 'after'
        val firstTerminatorAfter = after.indexOfFirst { TERMINATORS.contains(it) }
        val relevantAfter = if (firstTerminatorAfter >= 0) {
            after.substring(0, firstTerminatorAfter)
        } else {
            after
        }

        val combinedRaw = relevantBefore + relevantAfter
        val trimmedText = combinedRaw.trim()

        if (trimmedText.isEmpty()) {
            return null
        }

        return ExtractedSentence(
            textToCorrect = trimmedText,
            charsBeforeCursor = relevantBefore.length,
            charsAfterCursor = relevantAfter.length,
            isSelection = false
        )
    }
}
