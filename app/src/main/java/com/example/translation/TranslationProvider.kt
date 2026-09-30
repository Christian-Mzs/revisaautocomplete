package com.example.translation

interface TranslationProvider {
    suspend fun translate(
        text: String,
        sourceLanguage: String = "auto",
        targetLanguage: String
    ): TranslationResult
}
