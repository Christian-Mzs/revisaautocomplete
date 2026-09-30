package com.example.correction

import com.example.translation.GoogleGtxTranslationProvider
import com.example.translation.TranslationProvider
import com.example.translation.TranslationResult
import com.example.translation.TranslationService

class CorrectionService(
    private val translationService: TranslationService
) {
    constructor(provider: TranslationProvider = GoogleGtxTranslationProvider()) :
            this(TranslationService(provider))

    suspend fun correct(
        originalText: String,
        includeDiagnostics: Boolean = false
    ): CorrectionResult {
        val trimmed = originalText.trim()
        if (trimmed.isEmpty()) {
            return CorrectionResult(
                originalText = originalText,
                correctedText = originalText,
                isSuccess = true,
                durationMs = 0
            )
        }

        val totalStartTime = System.currentTimeMillis()

        // Step 1: Portuguese (or auto-detected) -> English
        val step1Result = translationService.translate(
            text = trimmed,
            sourceLanguage = "auto",
            targetLanguage = "en"
        )

        when (step1Result) {
            is TranslationResult.Error -> {
                val totalTime = System.currentTimeMillis() - totalStartTime
                return CorrectionResult(
                    originalText = originalText,
                    durationMs = totalTime,
                    isSuccess = false,
                    errorMessage = step1Result.errorMessage
                )
            }
            is TranslationResult.Success -> {
                val englishText = step1Result.translatedText
                val detectedLang = step1Result.detectedSourceLanguage

                // Step 2: English -> Portuguese
                val step2Result = translationService.translate(
                    text = englishText,
                    sourceLanguage = "en",
                    targetLanguage = "pt"
                )

                val totalDuration = System.currentTimeMillis() - totalStartTime

                return when (step2Result) {
                    is TranslationResult.Error -> {
                        CorrectionResult(
                            originalText = originalText,
                            intermediateText = englishText,
                            detectedSourceLanguage = detectedLang,
                            durationMs = totalDuration,
                            isSuccess = false,
                            errorMessage = step2Result.errorMessage
                        )
                    }
                    is TranslationResult.Success -> {
                        val correctedPortuguese = step2Result.translatedText

                        val diagnostic = if (includeDiagnostics) {
                            DiagnosticInfo(
                                characterCount = originalText.length,
                                detectedSourceLanguage = detectedLang,
                                firstCallStatus = step1Result.httpStatusCode,
                                firstCallLatencyMs = step1Result.latencyMs,
                                intermediateEnglishText = englishText,
                                secondCallStatus = step2Result.httpStatusCode,
                                secondCallLatencyMs = step2Result.latencyMs,
                                finalResult = correctedPortuguese,
                                totalTimeMs = totalDuration
                            )
                        } else null

                        CorrectionResult(
                            originalText = originalText,
                            intermediateText = englishText,
                            correctedText = correctedPortuguese,
                            detectedSourceLanguage = detectedLang,
                            durationMs = totalDuration,
                            isSuccess = true,
                            diagnosticInfo = diagnostic
                        )
                    }
                }
            }
        }
    }
}
