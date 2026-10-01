package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.settings.LanguagePreferences
import com.example.translation.SupportedLanguages
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class LanguagePreferencesTest {
    private val context: Context get() = ApplicationProvider.getApplicationContext()

    @Before fun resetPreferences() {
        context.getSharedPreferences("revisa_languages", Context.MODE_PRIVATE).edit().clear().commit()
    }

    @Test fun `defaults preserve original nine choices and Portuguese correction`() {
        val prefs = LanguagePreferences(context)
        assertEquals(SupportedLanguages.DEFAULT_CODES, prefs.translationLanguages.map { it.languageCode }.toSet())
        assertEquals("pt", prefs.correctionOutputLanguageCode)
        assertEquals("日本語", SupportedLanguages.find("ja")!!.displayName)
        assertEquals("Japanese", SupportedLanguages.find("ja")!!.secondaryName)
        assertEquals("Korean", SupportedLanguages.find("ko")!!.secondaryName)
    }

    @Test fun `adding removing and correction choice persist independently`() {
        val prefs = LanguagePreferences(context)
        assertTrue(prefs.setTranslationEnabled("ru", true))
        assertTrue(prefs.setTranslationEnabled("ja", false))
        assertTrue(prefs.setCorrectionOutputLanguage("ja"))
        val restored = LanguagePreferences(context)
        assertTrue(restored.translationLanguages.any { it.languageCode == "ru" })
        assertFalse(restored.translationLanguages.any { it.languageCode == "ja" })
        assertEquals("ja", restored.correctionOutputLanguageCode)
        val ordered = SupportedLanguages.getSortedWithPreferred("ja", restored.translationLanguages)
        assertFalse(ordered.any { it.languageCode == "ja" })
    }

    @Test fun `last selected language and unknown codes are protected`() {
        val prefs = LanguagePreferences(context)
        prefs.translationLanguages.filter { it.languageCode != "pt" }.forEach {
            assertTrue(prefs.setTranslationEnabled(it.languageCode, false))
        }
        assertFalse(prefs.setTranslationEnabled("pt", false))
        assertFalse(prefs.setTranslationEnabled("unknown", true))
        assertFalse(prefs.setCorrectionOutputLanguage("unknown"))
        assertEquals(listOf("pt"), prefs.translationLanguages.map { it.languageCode })
        assertEquals("pt", prefs.correctionOutputLanguageCode)
    }
}
