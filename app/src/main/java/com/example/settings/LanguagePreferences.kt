package com.example.settings

import android.content.Context
import com.example.translation.SupportedLanguages
import com.example.translation.TranslationTargetLanguage

class LanguagePreferences(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("revisa_languages", Context.MODE_PRIVATE)

    val translationLanguages: List<TranslationTargetLanguage>
        get() {
            val codes = prefs.getStringSet("translation_codes", null) ?: SupportedLanguages.DEFAULT_CODES
            return SupportedLanguages.ALL.filter { it.languageCode in codes }.ifEmpty {
                SupportedLanguages.ALL.filter { it.languageCode in SupportedLanguages.DEFAULT_CODES }
            }
        }

    fun setTranslationEnabled(code: String, enabled: Boolean): Boolean {
        val canonical = SupportedLanguages.find(code)?.languageCode ?: return false
        val codes = translationLanguages.map { it.languageCode }.toMutableSet()
        if (!enabled && canonical in codes && codes.size == 1) return false
        if (enabled) codes.add(canonical) else codes.remove(canonical)
        prefs.edit().putStringSet("translation_codes", codes).apply()
        return true
    }

    val correctionOutputLanguageCode: String
        get() = SupportedLanguages.find(prefs.getString("correction_output", "pt") ?: "pt")?.languageCode ?: "pt"

    fun setCorrectionOutputLanguage(code: String): Boolean {
        val canonical = SupportedLanguages.find(code)?.languageCode ?: return false
        prefs.edit().putString("correction_output", canonical).apply()
        return true
    }
}
