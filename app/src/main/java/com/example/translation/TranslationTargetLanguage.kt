package com.example.translation

data class TranslationTargetLanguage(
    val displayName: String,
    val languageCode: String
)

object SupportedLanguages {
    val ALL: List<TranslationTargetLanguage> = listOf(
        TranslationTargetLanguage("English", "en"),
        TranslationTargetLanguage("Español", "es"),
        TranslationTargetLanguage("Português", "pt"),
        TranslationTargetLanguage("Français", "fr"),
        TranslationTargetLanguage("Deutsch", "de"),
        TranslationTargetLanguage("Italiano", "it"),
        TranslationTargetLanguage("中文", "zh-CN"),
        TranslationTargetLanguage("日本語", "ja"),
        TranslationTargetLanguage("한국어", "ko")
    )

    fun getSortedWithPreferred(preferredCode: String): List<TranslationTargetLanguage> {
        val preferred = ALL.find { it.languageCode.equals(preferredCode, ignoreCase = true) }
        return if (preferred != null) {
            listOf(preferred) + ALL.filter { it.languageCode != preferred.languageCode }
        } else {
            ALL
        }
    }

    fun find(code: String): TranslationTargetLanguage? {
        return ALL.find { it.languageCode.equals(code, ignoreCase = true) }
    }
}
