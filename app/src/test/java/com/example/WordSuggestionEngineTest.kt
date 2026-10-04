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

    @Test fun `Portuguese ranks common surface forms and corrects missing accents`() {
        val engine = WordSuggestionEngine(context)

        assertEquals("não", engine.suggest("pt-BR", "nao").first())
        assertEquals(listOf("não"), engine.suggest("pt-BR", "nao"))
        assertEquals("você", engine.suggest("pt-BR", "voce").first())
        assertEquals("ônibus", engine.suggest("pt-BR", "onib").first())
        assertEquals("ônibus", engine.suggest("pt-BR", "oni").first())

        val shortPrefix = engine.suggest("pt-BR", "na")
        assertEquals(listOf("não", "nada", "nas"), shortPrefix)
        assertFalse(shortPrefix.any { it.equals("nabi", true) || it.equals("naã", true) })

        val acredit = engine.suggest("pt-BR", "acredit")
        assertEquals(listOf("acredito", "acreditar", "acredita"), acredit)
        assertFalse(acredit.any { it.equals("acreditivo", true) || it.equals("acreditação", true) })

        val test = engine.suggest("pt-BR", "test")
        assertEquals("teste", test.first())
        assertTrue(test.size in 1..3)
        assertTrue(engine.suggest("pt-BR", "csa").any { it.equals("casa", true) })
        assertTrue(engine.suggest("pt-BR", "cas").any { it.equals("casamento", true) })
    }

    @Test fun `selected language chooses its own frequency list`() {
        val engine = WordSuggestionEngine(context)
        assertTrue(engine.suggest("pt-BR", "test").any { it.equals("teste", true) })
        assertTrue(engine.suggest("en", "test").any { it.equals("testing", true) })
        assertTrue(engine.suggest("es", "test").any { it.equals("testigo", true) })
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
        assertEquals(listOf("pt-BR", "en", "es"), restored.activeLanguages.map { it.code })
        assertEquals("en", restored.cycle().code)
        assertEquals("es", restored.cycle().code)
        assertEquals("pt-BR", restored.cycle().code)
        assertEquals("en", restored.cycle().code)
        assertTrue(restored.setEnabled("en", false))
        assertTrue(restored.setEnabled("es", false))
        assertFalse(restored.setEnabled("pt-BR", false))
    }

    @Test fun `unsupported language falls back to Brazilian Portuguese`() {
        assertEquals("pt-BR", KeyboardLanguage.normalize("fr-FR"))
        assertEquals(listOf("pt-BR", "en", "es"), KeyboardLanguage.AVAILABLE.map { it.code })
    }
}
