package com.example.translation

data class TranslationTargetLanguage(
    val displayName: String,
    val languageCode: String,
    val secondaryName: String? = null
) {
    val promptName: String get() = when (languageCode) {
        "es" -> "Spanish"
        "pt" -> "Portuguese"
        "fr" -> "French"
        "de" -> "German"
        "it" -> "Italian"
        else -> secondaryName ?: displayName
    }
}

object SupportedLanguages {
    val ALL: List<TranslationTargetLanguage> = listOf(
        TranslationTargetLanguage("English", "en"),
        TranslationTargetLanguage("Español", "es"),
        TranslationTargetLanguage("Português", "pt"),
        TranslationTargetLanguage("Français", "fr"),
        TranslationTargetLanguage("Deutsch", "de"),
        TranslationTargetLanguage("Italiano", "it"),
        TranslationTargetLanguage("中文", "zh-CN", "Chinese (Simplified)"),
        TranslationTargetLanguage("日本語", "ja", "Japanese"),
        TranslationTargetLanguage("한국어", "ko", "Korean"),
        TranslationTargetLanguage("繁體中文", "zh-TW", "Chinese (Traditional)"),
        TranslationTargetLanguage("Arabic", "ar"),
        TranslationTargetLanguage("Russian", "ru"),
        TranslationTargetLanguage("Hindi", "hi"),
        TranslationTargetLanguage("Dutch", "nl"),
        TranslationTargetLanguage("Swedish", "sv"),
        TranslationTargetLanguage("Norwegian", "no"),
        TranslationTargetLanguage("Danish", "da"),
        TranslationTargetLanguage("Finnish", "fi"),
        TranslationTargetLanguage("Polish", "pl"),
        TranslationTargetLanguage("Turkish", "tr"),
        TranslationTargetLanguage("Greek", "el"),
        TranslationTargetLanguage("Ukrainian", "uk"),
        TranslationTargetLanguage("Hebrew", "he"),
        TranslationTargetLanguage("Indonesian", "id"),
        TranslationTargetLanguage("Thai", "th"),
        TranslationTargetLanguage("Vietnamese", "vi")
    )

    val DEFAULT_CODES: Set<String> = setOf("en", "es", "pt", "fr", "de", "it", "zh-CN", "ja", "ko")

    fun getSortedWithPreferred(preferredCode: String,
        available: List<TranslationTargetLanguage> = ALL): List<TranslationTargetLanguage> {
        val preferred = available.find { it.languageCode.equals(preferredCode, ignoreCase = true) }
        return if (preferred != null) {
            listOf(preferred) + available.filter { it.languageCode != preferred.languageCode }
        } else {
            available
        }
    }

    fun find(code: String): TranslationTargetLanguage? {
        return ALL.find { it.languageCode.equals(code, ignoreCase = true) }
    }
}
