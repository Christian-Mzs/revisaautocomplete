package com.example.correction

data class CorrectionResult(
    val originalText: String,
    val intermediateText: String? = null,
    val correctedText: String? = null,
    val detectedSourceLanguage: String? = null,
    val durationMs: Long = 0,
    val isSuccess: Boolean,
    val errorMessage: String? = null
)
