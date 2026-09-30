package com.example.translation

import java.util.Collections

class TranslationService(
    private val provider: TranslationProvider = GoogleGtxTranslationProvider(),
    private val maxCacheSize: Int = 60
) {
    // In-memory LRU cache decoupled from Android framework for pure JVM testability
    private val translationCache = Collections.synchronizedMap(
        object : LinkedHashMap<String, TranslationResult.Success>(maxCacheSize, 0.75f, true) {
            override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, TranslationResult.Success>?): Boolean {
                return size > maxCacheSize
            }
        }
    )

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
        val cached = translationCache[cacheKey]
        if (cached != null) {
            return cached
        }

        val result = provider.translate(
            text = text,
            sourceLanguage = sourceLanguage,
            targetLanguage = targetLanguage
        )

        if (result is TranslationResult.Success) {
            translationCache[cacheKey] = result
        }

        return result
    }

    fun clearCache() {
        translationCache.clear()
    }
}
