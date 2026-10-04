package com.example

import androidx.test.core.app.ApplicationProvider
import com.example.suggestions.KeyboardLanguage
import com.example.suggestions.KeyboardLanguagePreferences
import com.example.suggestions.WordSuggestionEngine
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class WordSuggestionEngineTest {
    private val context get() = ApplicationProvider.getApplicationContext<android.content.Context>()

    @Test fun `Portuguese accents and prefixes complete from Portuguese dataset`() {
        val engine = WordSuggestionEngine(context)
        assertTrue(engine.suggest("pt-BR", "nao").any { it.equals("não", true) })
        assertTrue(engine.suggest("pt-BR", "voce").any { it.equals("você", true) })
        assertTrue(engine.suggest("pt-BR", "onib").any { it.equals("ônibus", true) })
        val test = engine.suggest("pt-BR", "test")
        assertTrue(test.size in 1..3)
        assertTrue(test.any { it.startsWith("test", true) })
    }

    @Test fun `selected language chooses its own dictionary`() {
        val engine = WordSuggestionEngine(context)
        assertTrue(engine.suggest("pt-BR", "cas").any { it.equals("casa", true) })
        assertTrue(engine.suggest("en", "test").isNotEmpty())
        assertTrue(engine.suggest("es", "cas").any { it.equals("casa", true) })
        assertTrue(engine.suggest("en", "test", 3).size <= 3)
    }

    @Test fun `first language comes from the system once and choices persist and cycle in order`() {
        context.getSharedPreferences("keyboard_languages", 0).edit().clear().commit()
        val first = KeyboardLanguagePreferences(context)
        first.ensureInitialized("es-MX")
        assertEquals("es", first.currentLanguage.code)
        context.getSharedPreferences("keyboard_languages", 0).edit().clear().commit()
        first.ensureInitialized("pt-BR")
        assertTrue(first.setEnabled("pt-BR", true))
        assertTrue(first.setEnabled("en", true))
        assertTrue(first.setEnabled("es", true))
        assertTrue(first.select("pt-BR"))

        val restored = KeyboardLanguagePreferences(context)
        restored.ensureInitialized("en-US") // Must not override the user's saved selection.
        assertEquals(listOf("es", "pt-BR", "en"), restored.activeLanguages.map { it.code })
        assertEquals("pt-BR", restored.cycle().code)
        assertEquals("en", restored.cycle().code)
        assertEquals("es", restored.cycle().code)
        assertEquals("pt-BR", restored.cycle().code)
        assertTrue(restored.setEnabled("en", false))
        assertTrue(restored.setEnabled("es", false))
        assertFalse(restored.setEnabled("pt-BR", false))
    }

    @Test fun `unsupported language falls back to Brazilian Portuguese`() {
        assertEquals("pt-BR", KeyboardLanguage.normalize("fr-FR"))
        assertEquals(listOf("pt-BR", "en", "es"), KeyboardLanguage.AVAILABLE.map { it.code })
    }
}
