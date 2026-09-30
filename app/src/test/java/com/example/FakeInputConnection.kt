package com.example

import android.os.Bundle
import android.os.Handler
import android.view.KeyEvent
import android.view.inputmethod.CompletionInfo
import android.view.inputmethod.CorrectionInfo
import android.view.inputmethod.ExtractedText
import android.view.inputmethod.ExtractedTextRequest
import android.view.inputmethod.InputConnection
import android.view.inputmethod.InputContentInfo
import kotlin.math.max
import kotlin.math.min

open class FakeInputConnection(
    initialText: String = "",
    initialCursor: Int = initialText.length,
    initialSelectionEnd: Int = initialCursor
) : InputConnection {

    private val textBuffer = StringBuilder(initialText)
    var cursorStart: Int = min(initialCursor, initialSelectionEnd)
        private set
    var cursorEnd: Int = max(initialCursor, initialSelectionEnd)
        private set

    val currentText: String
        get() = textBuffer.toString()

    fun setSelectionRange(start: Int, end: Int) {
        cursorStart = min(start, end)
        cursorEnd = max(start, end)
    }

    override fun getTextBeforeCursor(n: Int, flags: Int): CharSequence? {
        val start = max(0, cursorStart - n)
        return textBuffer.substring(start, cursorStart)
    }

    override fun getTextAfterCursor(n: Int, flags: Int): CharSequence? {
        val end = min(textBuffer.length, cursorEnd + n)
        return textBuffer.substring(cursorEnd, end)
    }

    override fun getSelectedText(flags: Int): CharSequence? {
        if (cursorStart == cursorEnd) return null
        return textBuffer.substring(cursorStart, cursorEnd)
    }

    override fun deleteSurroundingText(beforeLength: Int, afterLength: Int): Boolean {
        // Delete characters before cursor
        val deleteBeforeStart = max(0, cursorStart - beforeLength)
        textBuffer.delete(deleteBeforeStart, cursorStart)
        val delta = cursorStart - deleteBeforeStart
        cursorStart = deleteBeforeStart
        cursorEnd -= delta

        // Delete characters after cursor
        val deleteAfterEnd = min(textBuffer.length, cursorEnd + afterLength)
        textBuffer.delete(cursorEnd, deleteAfterEnd)

        return true
    }

    override fun commitText(text: CharSequence?, newCursorPosition: Int): Boolean {
        val str = text?.toString().orEmpty()
        textBuffer.replace(cursorStart, cursorEnd, str)
        cursorStart += str.length
        cursorEnd = cursorStart
        return true
    }

    override fun beginBatchEdit(): Boolean = true
    override fun endBatchEdit(): Boolean = true

    // Stubs for required interface methods
    override fun getCursorCapsMode(reqModes: Int): Int = 0
    override fun getExtractedText(request: ExtractedTextRequest?, flags: Int): ExtractedText? = null
    override fun deleteSurroundingTextInCodePoints(beforeLength: Int, afterLength: Int): Boolean = false
    override fun setComposingText(text: CharSequence?, newCursorPosition: Int): Boolean = false
    override fun setComposingRegion(start: Int, end: Int): Boolean = false
    override fun finishComposingText(): Boolean = false
    override fun commitCompletion(text: CompletionInfo?): Boolean = false
    override fun commitCorrection(correctionInfo: CorrectionInfo?): Boolean = false
    override fun setSelection(start: Int, end: Int): Boolean {
        setSelectionRange(start, end)
        return true
    }
    override fun performEditorAction(editorAction: Int): Boolean = true
    override fun performContextMenuAction(id: Int): Boolean = true
    override fun clearMetaKeyStates(states: Int): Boolean = true
    override fun reportFullscreenMode(enabled: Boolean): Boolean = false
    override fun performPrivateCommand(action: String?, data: Bundle?): Boolean = false
    override fun requestCursorUpdates(cursorUpdateMode: Int): Boolean = false
    override fun getHandler(): Handler? = null
    override fun closeConnection() {}
    override fun commitContent(inputContentInfo: InputContentInfo, flags: Int, opts: Bundle?): Boolean = false
    override fun sendKeyEvent(event: KeyEvent?): Boolean = false
}
