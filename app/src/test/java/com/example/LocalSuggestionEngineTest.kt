package com.example

import com.example.ime.LocalSuggestionEngine
import org.junit.Assert.*
import org.junit.Test

class LocalSuggestionEngineTest {
    @Test fun `ranks common accents first and keeps original as candidate`() {
        assertEquals(listOf("não", "nao"), LocalSuggestionEngine.suggest("nao"))
        assertEquals(listOf("você", "voce", "vocês"), LocalSuggestionEngine.suggest("voce"))
        for ((typed, expected) in mapOf("tambem" to "também", "so" to "só", "ja" to "já",
            "ate" to "até", "sera" to "será", "facil" to "fácil", "dificil" to "difícil")) {
            assertEquals(expected, LocalSuggestionEngine.suggest(typed).first())
        }
    }

    @Test fun `ambiguous words keep both forms and preserve capitalization`() {
        assertEquals(listOf("esta", "está"), LocalSuggestionEngine.suggest("esta"))
        assertEquals(listOf("Não", "Nao"), LocalSuggestionEngine.suggest("Nao"))
        assertEquals(listOf("VOCÊ", "VOCE", "VOCÊS"), LocalSuggestionEngine.suggest("VOCE"))
        assertEquals("não", LocalSuggestionEngine.suggest("não").first())
    }

    @Test fun `unknown words stay neutral and completions are bounded`() {
        assertTrue(LocalSuggestionEngine.suggest("xyzzy").isEmpty())
        assertTrue(LocalSuggestionEngine.suggest("").isEmpty())
        assertTrue(LocalSuggestionEngine.suggest("v").isEmpty())
        val candidates = LocalSuggestionEngine.suggest("vo")
        assertTrue("você" in candidates)
        assertTrue(candidates.size <= 3)
        assertEquals(candidates.distinct(), candidates)
    }
}
