package com.example.codex

interface CodexTextEngine {
    suspend fun ensureRuntimeReady()
    suspend fun loginStatus(): LoginState
    suspend fun processText(text: String, operation: TextOperation): String
}

object RuntimeReadiness {
    fun matches(expected: String, validated: String?, filesPresent: Boolean): Boolean =
        filesPresent && expected.isNotBlank() && validated == expected
}

class CodexLoginRequiredException : IllegalStateException("Entre com ChatGPT para usar este recurso.")
