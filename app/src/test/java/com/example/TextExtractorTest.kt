package com.example

import android.view.inputmethod.ExtractedText
import android.view.inputmethod.ExtractedTextRequest
import com.example.ime.TextExtractor
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class TextExtractorTest {
    @Test fun `all punctuation whitespace and cursor positions preserve the whole field`() {
        for (text in listOf("Eu gostei disso. Mas não sei se devo fazer isso",
            "Você acha isso bom? Eu não tenho certeza. talvez eu faça isso",
            "Olá!\nTudo bem?\nVamos amanhã.", "  \n abc def ghi \n ", "a".repeat(1000))) {
            for (cursor in listOf(0, text.length / 2, text.length)) {
                val range = TextExtractor.extract(FakeInputConnection(text, cursor))!!
                assertEquals(text, range.textToCorrect)
                assertEquals(cursor, range.charsBeforeCursor)
                assertEquals(text.length - cursor, range.charsAfterCursor)
                assertFalse(range.isSelection)
            }
        }
    }

    @Test fun `selection has absolute priority including multiple sentences and whitespace`() {
        for (selection in listOf("não sei se devo fazer isso", " primeira. segunda!\nterceira? ", "  ")) {
            val ic = object : FakeInputConnection("prefix$selection suffix", 6, 6 + selection.length) {
                override fun getExtractedText(request: ExtractedTextRequest?, flags: Int): ExtractedText? = error("Private surrounding text read")
                override fun getTextBeforeCursor(n: Int, flags: Int): CharSequence = error("Private prefix read")
                override fun getTextAfterCursor(n: Int, flags: Int): CharSequence = error("Private suffix read")
            }
            val range = TextExtractor.extract(ic)
            if (selection.isBlank()) assertNull(range) else {
                assertEquals(selection, range!!.textToCorrect)
                assertTrue(range.isSelection)
            }
        }
    }

    @Test fun `empty and blank fields do not process`() {
        for (text in listOf("", "   ", "\n\t ")) assertNull(TextExtractor.extract(FakeInputConnection(text)))
        assertNull(TextExtractor.extract(null))
    }

    @Test fun `complete extracted text is preferred without surrounding cursor reads`() {
        val text = " prefix.\nwhole field! "
        val ic = object : FakeInputConnection(text, 5) {
            override fun getExtractedText(request: ExtractedTextRequest?, flags: Int) = ExtractedText().apply {
                this.text = currentText; startOffset = 0; partialStartOffset = -1
                selectionStart = cursorStart; selectionEnd = cursorEnd
            }
            override fun getTextBeforeCursor(n: Int, flags: Int): CharSequence = error("Fallback should not run")
            override fun getTextAfterCursor(n: Int, flags: Int): CharSequence = error("Fallback should not run")
        }
        val range = TextExtractor.extract(ic)!!
        assertEquals(text, range.textToCorrect)
        assertTrue(range.wholeField)
        assertEquals(5, range.charsBeforeCursor)
    }

    @Test fun `partial extracted snapshot uses bounded fallback and capped fallback is refused`() {
        val ic = object : FakeInputConnection("full field", 4) {
            override fun getExtractedText(request: ExtractedTextRequest?, flags: Int) = ExtractedText().apply {
                text = "field"; startOffset = 5; partialStartOffset = -1
            }
        }
        assertEquals("full field", TextExtractor.extract(ic)!!.textToCorrect)
        assertNull(TextExtractor.extract(FakeInputConnection("a".repeat(TextExtractor.FALLBACK_LIMIT))))
    }
}
