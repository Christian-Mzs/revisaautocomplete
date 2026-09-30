package com.example.translation

sealed class TranslationResult {
    data class Success(
        val translatedText: String,
        val detectedSourceLanguage: String?,
        val httpStatusCode: Int = 200,
        val latencyMs: Long = 0
    ) : TranslationResult()

    data class Error(
        val errorMessage: String,
        val httpStatusCode: Int = 0,
        val errorType: ErrorType
    ) : TranslationResult()

    enum class ErrorType {
        NO_CONNECTION,
        TIMEOUT,
        RATE_LIMITED,
        SERVICE_UNAVAILABLE,
        EMPTY_RESPONSE,
        UNKNOWN
    }
}
