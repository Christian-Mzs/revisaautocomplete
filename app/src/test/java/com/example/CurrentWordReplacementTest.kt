package com.example

import android.text.InputType
import android.view.inputmethod.EditorInfo
import com.example.ime.CurrentWord
import com.example.ime.CurrentWordExtractor
import com.example.ime.CurrentWordReplacement
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class CurrentWordReplacementTest {
    private fun editor() = EditorInfo().apply { inputType = InputType.TYPE_CLASS_TEXT }

    @Test fun `replaces only word in middle of sentence including suffix after cursor`() {
        val ic = FakeInputConnection("oi, voce! tudo bem", 6)
        val info = editor()
        val word = CurrentWordExtractor.extract(ic, info)!!
        assertTrue(CurrentWordReplacement.replace(ic, info, word, "você"))
        assertEquals("oi, você! tudo bem", ic.currentText)
        assertEquals(8, ic.cursorStart)
        assertEquals(ic.cursorStart, ic.cursorEnd)
    }

    @Test fun `stale candidate and selected text do not change sentence`() {
        val ic = FakeInputConnection("nao")
        val info = editor()
        val word = CurrentWordExtractor.extract(ic, info)!!
        ic.commitText("x", 1)
        assertFalse(CurrentWordReplacement.replace(ic, info, word, "não"))
        assertEquals("naox", ic.currentText)
        ic.setSelectionRange(0, 3)
        assertFalse(CurrentWordReplacement.replace(ic, info, word, "não"))
        assertEquals("naox", ic.currentText)
    }

    @Test fun `password transition and invalid candidates block replacement`() {
        val ic = object : FakeInputConnection("nao") {
            override fun getTextBeforeCursor(n: Int, flags: Int): CharSequence? = error("Password read")
            override fun getTextAfterCursor(n: Int, flags: Int): CharSequence? = error("Password read")
            override fun getSelectedText(flags: Int): CharSequence? = error("Password read")
        }
        val password = EditorInfo().apply {
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
        }
        assertFalse(CurrentWordReplacement.replace(ic, password, CurrentWord("nao", ""), "não"))
        assertFalse(CurrentWordReplacement.replace(ic, editor(), CurrentWord("nao", ""), "unrelated"))
        assertEquals("nao", ic.currentText)
    }

    @Test fun `failed deletion prevents commit and ends batch`() {
        var committed = false
        var ended = false
        val ic = object : FakeInputConnection("nao") {
            override fun deleteSurroundingText(beforeLength: Int, afterLength: Int) = false
            override fun commitText(text: CharSequence?, newCursorPosition: Int): Boolean {
                committed = true
                return super.commitText(text, newCursorPosition)
            }
            override fun endBatchEdit(): Boolean { ended = true; return true }
        }
        assertFalse(CurrentWordReplacement.replace(ic, editor(), CurrentWord("nao", ""), "não"))
        assertFalse(committed)
        assertTrue(ended)
        assertEquals("nao", ic.currentText)
    }
}
