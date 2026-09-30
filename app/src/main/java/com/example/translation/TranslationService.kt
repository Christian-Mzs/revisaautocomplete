package com.example.translation

import android.util.LruCache

class TranslationService(
    private val provider: TranslationProvider = GoogleGtxTranslationProvider()
) {
    // In-memory cache: "source:target:text" -> TranslationResult.Success
    private val translationCache = LruCache<String, TranslationResult.Success>(60)

    suspend fun translate(
        text: String,
        sourceLanguage: String = "auto",
        targetLanguage: String
    ): TranslationResult {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) {
            return TranslationResult.Success(
                translatedText = text,
                detectedSourceLanguage = sourceLanguage,
                httpStatusCode = 200,
                latencyMs = 0
            )
        }

        val cacheKey = "$sourceLanguage:$targetLanguage:$trimmed"
        val cached = translationCache.get(cacheKey)
        if (cached != null) {
            return cached
        }

        val result = provider.translate(
            text = text,
            sourceLanguage = sourceLanguage,
            targetLanguage = targetLanguage
        )

        if (result is TranslationResult.Success) {
            translationCache.put(cacheKey, result)
        }

        return result
    }

    fun clearCache() {
        translationCache.evictAll()
    }
}
