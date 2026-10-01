package com.example

import android.view.inputmethod.ExtractedText
import android.view.inputmethod.ExtractedTextRequest
import com.example.ime.TextExtractor
import com.example.ime.TextReplacementController
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class TextReplacementControllerTest {
    @Test fun `whole field replaces exactly once at beginning middle and end on both routes`() {
        val original = "  Olá. eu nao sei!\nDepois faço isso?  "
        for (useSnapshot in listOf(false, true)) for (cursor in listOf(0, original.length / 2, original.length)) {
            var begins = 0; var ends = 0
            val ic = object : FakeInputConnection(original, cursor) {
                override fun getExtractedText(request: ExtractedTextRequest?, flags: Int): ExtractedText? =
                    if (!useSnapshot) null else ExtractedText().apply {
                        text = currentText; partialStartOffset = -1; startOffset = 0
                        selectionStart = cursorStart; selectionEnd = cursorEnd
                    }
                override fun beginBatchEdit(): Boolean { begins++; return true }
                override fun endBatchEdit(): Boolean { ends++; return true }
            }
            val extracted = TextExtractor.extract(ic)!!
            ic.setSelectionRange(original.length / 3, original.length / 3)
            assertTrue(TextReplacementController.replace(ic, extracted, "Resultado."))
            assertEquals("Resultado.", ic.currentText)
            assertEquals("Resultado.".length, ic.cursorStart)
            assertEquals(ic.cursorStart, ic.cursorEnd)
            assertEquals(1, begins); assertEquals(1, ends)
        }
    }

    @Test fun `selection replaces only its exact span preserving surrounding text`() {
        val original = "Início: [ trecho. com erro!\n ] :Fim"
        val start = original.indexOf('['); val end = original.indexOf(']') + 1
        val ic = FakeInputConnection(original, start, end)
        val range = TextExtractor.extract(ic)!!
        assertTrue(TextReplacementController.replace(ic, range, "[corrigido]"))
        assertEquals("Início: [corrigido] :Fim", ic.currentText)
        assertEquals(start + "[corrigido]".length, ic.cursorStart)
    }

    @Test fun `changed content or selection cannot overwrite a stale preview`() {
        val ic = FakeInputConnection("original")
        val range = TextExtractor.extract(ic)!!
        ic.commitText("novo", 1)
        assertFalse(TextReplacementController.replace(ic, range, "resultado"))
        assertEquals("originalnovo", ic.currentText)
        ic.setSelectionRange(0, 3)
        assertFalse(TextReplacementController.replace(ic, range, "resultado"))
    }

    @Test fun `editor refusing deletion never receives replacement commit and ends batch`() {
        var ended = false
        val ic = object : FakeInputConnection("original") {
            override fun deleteSurroundingText(beforeLength: Int, afterLength: Int) = false
            override fun commitText(text: CharSequence?, newCursorPosition: Int): Boolean = error("Unsafe commit")
            override fun endBatchEdit(): Boolean { ended = true; return true }
        }
        assertFalse(TextReplacementController.replace(ic, TextExtractor.extract(ic)!!, "resultado"))
        assertEquals("original", ic.currentText)
        assertTrue(ended)
    }
}
