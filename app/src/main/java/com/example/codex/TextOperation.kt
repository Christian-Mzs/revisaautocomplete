package com.example.codex

typealias TargetLanguage = com.example.translation.TranslationTargetLanguage

object TranslationLanguages {
    val all: List<TargetLanguage> get() = com.example.translation.SupportedLanguages.ALL
}

enum class TextMode { CORRECTION, TRANSLATION }

data class TextOperation(
    val mode: TextMode = TextMode.CORRECTION,
    val targetLanguage: TargetLanguage = TranslationLanguages.all.first()
) {
    fun canExecute(runtimeReady: Boolean, connected: Boolean, busy: Boolean, text: String): Boolean =
        runtimeReady && connected && !busy && text.isNotBlank()

    fun prompt(text: String): String = when (mode) {
        TextMode.CORRECTION -> CodexPrompts.correction(text)
        TextMode.TRANSLATION -> CodexPrompts.translation(text, targetLanguage)
    }
    fun command(): List<String> = CodexCommands.textExecution()
}
