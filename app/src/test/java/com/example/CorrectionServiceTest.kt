package com.example

import com.example.correction.CorrectionService
import com.example.translation.TranslationProvider
import com.example.translation.TranslationResult
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

class FakeTranslationProvider(
    private val translations: Map<Pair<String, String>, String> = emptyMap(),
    private val shouldFail: Boolean = false,
    private val failureError: TranslationResult.Error = TranslationResult.Error(
        errorMessage = "Sem conexão para corrigir.",
        errorType = TranslationResult.ErrorType.NO_CONNECTION
    )
) : TranslationProvider {

    override suspend fun translate(
        text: String,
        sourceLanguage: String,
        targetLanguage: String
    ): TranslationResult {
        if (shouldFail) {
            return failureError
        }

        val key = Pair(text, targetLanguage)
        val resultText = translations[key] ?: "translated_$text"
        return TranslationResult.Success(
            translatedText = resultText,
            detectedSourceLanguage = if (sourceLanguage == "auto") "pt" else sourceLanguage,
            httpStatusCode = 200,
            latencyMs = 15
        )
    }
}

class CorrectionServiceTest {

    @Test
    fun `two-step translation PT to EN to PT normalizes text`() = runTest {
        val fakeProvider = FakeTranslationProvider(
            translations = mapOf(
                Pair("eu nao sei se ele vai vim", "en") to "I do not know if he will come",
                Pair("I do not know if he will come", "pt") to "Eu não sei se ele vai vir."
            )
        )

        val service = CorrectionService(provider = fakeProvider)

        val result = service.correct("eu nao sei se ele vai vim", includeDiagnostics = true)

        assertTrue(result.isSuccess)
        assertEquals("eu nao sei se ele vai vim", result.originalText)
        assertEquals("I do not know if he will come", result.intermediateText)
        assertEquals("Eu não sei se ele vai vir.", result.correctedText)
        assertNotNull(result.diagnosticInfo)
        assertEquals("I do not know if he will come", result.diagnosticInfo?.intermediateEnglishText)
        assertEquals("Eu não sei se ele vai vir.", result.diagnosticInfo?.finalResult)
    }

    @Test
    fun `returns error gracefully when first translation step fails`() = runTest {
        val fakeProvider = FakeTranslationProvider(
            shouldFail = true,
            failureError = TranslationResult.Error(
                errorMessage = "Sem conexão para corrigir.",
                errorType = TranslationResult.ErrorType.NO_CONNECTION
            )
        )

        val service = CorrectionService(provider = fakeProvider)

        val result = service.correct("texto qualquer")

        assertFalse(result.isSuccess)
        assertEquals("Sem conexão para corrigir.", result.errorMessage)
        assertNull(result.correctedText)
    }

    @Test
    fun `handles blank text gracefully`() = runTest {
        val fakeProvider = FakeTranslationProvider()
        val service = CorrectionService(provider = fakeProvider)

        val result = service.correct("   ")

        assertTrue(result.isSuccess)
        assertEquals("   ", result.correctedText)
    }
}
