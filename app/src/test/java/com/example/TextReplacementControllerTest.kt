package com.example

import com.example.ime.SentenceExtractor
import com.example.ime.TextReplacementController
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TextReplacementControllerTest {

    @Test
    fun `replaces middle sentence preserving prefix and suffix text and punctuation`() {
        val original = "Olá. eu nao sei se ele vem hoje. Depois faço isso."
        // Cursor placed at the word 'hoje'
        val cursor = original.indexOf("hoje") + "hoje".length
        val ic = FakeInputConnection(original, initialCursor = cursor)

        val extracted = SentenceExtractor.extract(ic)
        assertTrue(extracted != null)

        val correctedText = "Eu não sei se ele vem hoje"
        val replaced = TextReplacementController.replace(ic, extracted!!, correctedText)
        assertTrue(replaced)

        val expected = "Olá. Eu não sei se ele vem hoje. Depois faço isso."
        assertEquals(expected, ic.currentText)
    }

    @Test
    fun `replaces sentence at end of text preserving prefix`() {
        val original = "Olá. eu nao sei se ele vem hoje"
        val ic = FakeInputConnection(original, initialCursor = original.length)

        val extracted = SentenceExtractor.extract(ic)
        assertTrue(extracted != null)

        val correctedText = "Eu não sei se ele vem hoje."
        val replaced = TextReplacementController.replace(ic, extracted!!, correctedText)
        assertTrue(replaced)

        val expected = "Olá. Eu não sei se ele vem hoje."
        assertEquals(expected, ic.currentText)
    }

    @Test
    fun `replaces explicit selection preserving outer text`() {
        val original = "Início: [trecho com erro] :Fim"
        val start = original.indexOf("[trecho com erro]")
        val end = start + "[trecho com erro]".length

        val ic = FakeInputConnection(original, initialCursor = start, initialSelectionEnd = end)

        val extracted = SentenceExtractor.extract(ic)
        assertTrue(extracted != null)
        assertTrue(extracted!!.isSelection)

        val corrected = "[trecho corrigido]"
        val replaced = TextReplacementController.replace(ic, extracted, corrected)
        assertTrue(replaced)

        val expected = "Início: [trecho corrigido] :Fim"
        assertEquals(expected, ic.currentText)
    }
}
