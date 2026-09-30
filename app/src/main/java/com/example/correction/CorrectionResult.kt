package com.example.correction

data class DiagnosticInfo(
    val characterCount: Int,
    val detectedSourceLanguage: String?,
    val firstCallStatus: Int,
    val firstCallLatencyMs: Long,
    val intermediateEnglishText: String,
    val secondCallStatus: Int,
    val secondCallLatencyMs: Long,
    val finalResult: String,
    val totalTimeMs: Long
)

data class CorrectionResult(
    val originalText: String,
    val intermediateText: String? = null,
    val correctedText: String? = null,
    val detectedSourceLanguage: String? = null,
    val durationMs: Long = 0,
    val isSuccess: Boolean,
    val errorMessage: String? = null,
    val diagnosticInfo: DiagnosticInfo? = null
)
