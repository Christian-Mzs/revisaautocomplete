package com.example

import com.example.correction.CorrectionService
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

class ConfiguredCorrectionTest {
    @Test fun `configured output retains automatic detection and English intermediate`() = runTest {
        val provider = FakeTranslationProvider(translations = mapOf(
            ("olá" to "en") to "hello",
            ("hello" to "es") to "hola"
        ))
        val result = CorrectionService(provider = provider).correct("olá", "es")
        assertTrue(result.isSuccess)
        assertEquals("hola", result.correctedText)
        assertEquals(listOf(Triple("olá", "auto", "en"), Triple("hello", "en", "es")), provider.calls)
    }

    @Test fun `English output avoids a redundant English to English request`() = runTest {
        val provider = FakeTranslationProvider(translations = mapOf(("olá" to "en") to "hello"))
        val result = CorrectionService(provider = provider).correct("olá", "en")
        assertTrue(result.isSuccess)
        assertEquals("hello", result.correctedText)
        assertEquals(listOf(Triple("olá", "auto", "en")), provider.calls)
    }
}
