package com.example

import com.example.ime.SentenceExtractor
import org.junit.Assert.*
import org.junit.Test

class SentenceExtractorTest {

    @Test
    fun `extracts sentence after period with cursor at the end`() {
        val text = "Olá. eu nao sei se ele vem hoje"
        val ic = FakeInputConnection(text, initialCursor = text.length)

        val extracted = SentenceExtractor.extract(ic)
        assertNotNull(extracted)
        assertEquals("eu nao sei se ele vem hoje", extracted?.textToCorrect)
        assertFalse(extracted?.isSelection ?: true)
    }

    @Test
    fun `extracts sentence with cursor at the beginning`() {
        val text = "eu nao sei se ele vem hoje. Depois faço isso."
        val ic = FakeInputConnection(text, initialCursor = 0)

        val extracted = SentenceExtractor.extract(ic)
        assertNotNull(extracted)
        assertEquals("eu nao sei se ele vem hoje", extracted?.textToCorrect)
    }

    @Test
    fun `extracts sentence with cursor in the middle`() {
        val text = "eu nao sei se ele vem hoje"
        // Cursor between 'sei ' and 'se' (index 11)
        val ic = FakeInputConnection(text, initialCursor = 11)

        val extracted = SentenceExtractor.extract(ic)
        assertNotNull(extracted)
        assertEquals("eu nao sei se ele vem hoje", extracted?.textToCorrect)
        assertEquals(11, extracted?.charsBeforeCursor)
        assertEquals(15, extracted?.charsAfterCursor)
    }

    @Test
    fun `handles question mark terminator`() {
        val text = "Tudo bem? eu nao sei se ele vai"
        val ic = FakeInputConnection(text, initialCursor = text.length)

        val extracted = SentenceExtractor.extract(ic)
        assertNotNull(extracted)
        assertEquals("eu nao sei se ele vai", extracted?.textToCorrect)
    }

    @Test
    fun `handles exclamation mark terminator`() {
        val text = "Cuidado! eu nao vi nada"
        val ic = FakeInputConnection(text, initialCursor = text.length)

        val extracted = SentenceExtractor.extract(ic)
        assertNotNull(extracted)
        assertEquals("eu nao vi nada", extracted?.textToCorrect)
    }

    @Test
    fun `handles newline terminator`() {
        val text = "Primeira linha\neu nao sei se ele vai"
        val ic = FakeInputConnection(text, initialCursor = text.length)

        val extracted = SentenceExtractor.extract(ic)
        assertNotNull(extracted)
        assertEquals("eu nao sei se ele vai", extracted?.textToCorrect)
    }

    @Test
    fun `handles multiple spaces after terminator`() {
        val text = "Olá.     eu nao sei se ele vem hoje"
        val ic = FakeInputConnection(text, initialCursor = text.length)

        val extracted = SentenceExtractor.extract(ic)
        assertNotNull(extracted)
        assertEquals("eu nao sei se ele vem hoje", extracted?.textToCorrect)
        // Ensure charsBeforeCursor does not include the 5 spaces and dot
        assertEquals("eu nao sei se ele vem hoje".length, extracted?.charsBeforeCursor)
    }

    @Test
    fun `explicit selection takes priority and extracts only selected text`() {
        val text = "Texto antes. Trecho selecionado com erro. Texto depois."
        val selStart = text.indexOf("Trecho selecionado com erro")
        val selEnd = selStart + "Trecho selecionado com erro".length

        val ic = FakeInputConnection(text, initialCursor = selStart, initialSelectionEnd = selEnd)

        val extracted = SentenceExtractor.extract(ic)
        assertNotNull(extracted)
        assertEquals("Trecho selecionado com erro", extracted?.textToCorrect)
        assertTrue(extracted?.isSelection ?: false)
    }

    @Test
    fun `respects maxLookback boundary`() {
        val longPrefix = "a".repeat(100)
        val text = "$longPrefix eu nao sei"
        val ic = FakeInputConnection(text, initialCursor = text.length)

        // With small lookback of 15, should only read 15 chars before cursor
        val extracted = SentenceExtractor.extract(ic, maxLookback = 15, maxLookahead = 50)
        assertNotNull(extracted)
        assertTrue(extracted!!.charsBeforeCursor <= 15)
    }

    @Test
    fun `returns null on empty input`() {
        val ic = FakeInputConnection("", initialCursor = 0)
        val extracted = SentenceExtractor.extract(ic)
        assertNull(extracted)
    }
}
