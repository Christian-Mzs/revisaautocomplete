package com.example

import com.example.translation.TranslationResult
import com.example.translation.TranslationService
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

class TranslationServiceTest {

    @Test
    fun `translate performs single direct translation call with source auto`() = runTest {
        val fakeProvider = FakeTranslationProvider(
            translations = mapOf(
                Pair("bom dia", "en") to "good morning"
            )
        )
        val service = TranslationService(provider = fakeProvider)

        val result = service.translate(
            text = "bom dia",
            sourceLanguage = "auto",
            targetLanguage = "en"
        )

        assertTrue(result is TranslationResult.Success)
        val success = result as TranslationResult.Success
        assertEquals("good morning", success.translatedText)

        // Verify exactly one call was made to the provider
        assertEquals(1, fakeProvider.callCount)
        assertEquals("bom dia", fakeProvider.calls[0].first)
        assertEquals("auto", fakeProvider.calls[0].second)
        assertEquals("en", fakeProvider.calls[0].third)
    }

    @Test
    fun `translates between various language pairs without network`() = runTest {
        val fakeProvider = FakeTranslationProvider(
            translations = mapOf(
                Pair("hello", "pt") to "olá",
                Pair("obrigado", "es") to "gracias",
                Pair("obrigado", "ja") to "ありがとう"
            )
        )
        val service = TranslationService(provider = fakeProvider)

        val r1 = service.translate("hello", "en", "pt")
        assertTrue(r1 is TranslationResult.Success)
        assertEquals("olá", (r1 as TranslationResult.Success).translatedText)

        val r2 = service.translate("obrigado", "pt", "es")
        assertTrue(r2 is TranslationResult.Success)
        assertEquals("gracias", (r2 as TranslationResult.Success).translatedText)

        val r3 = service.translate("obrigado", "pt", "ja")
        assertTrue(r3 is TranslationResult.Success)
        assertEquals("ありがとう", (r3 as TranslationResult.Success).translatedText)

        assertEquals(3, fakeProvider.callCount)
    }

    @Test
    fun `cache prevents redundant calls for identical text and target`() = runTest {
        val fakeProvider = FakeTranslationProvider(
            translations = mapOf(
                Pair("bom dia", "en") to "good morning",
                Pair("bom dia", "es") to "buenos días"
            )
        )
        val service = TranslationService(provider = fakeProvider)

        // First call: provider is called
        val res1 = service.translate("bom dia", "auto", "en")
        assertEquals(1, fakeProvider.callCount)
        assertEquals("good morning", (res1 as TranslationResult.Success).translatedText)

        // Second call with exact same parameters: uses cache, provider NOT called again
        val res2 = service.translate("bom dia", "auto", "en")
        assertEquals(1, fakeProvider.callCount)
        assertEquals("good morning", (res2 as TranslationResult.Success).translatedText)

        // Third call with different target language: provider MUST be called
        val res3 = service.translate("bom dia", "auto", "es")
        assertEquals(2, fakeProvider.callCount)
        assertEquals("buenos días", (res3 as TranslationResult.Success).translatedText)
    }
}
