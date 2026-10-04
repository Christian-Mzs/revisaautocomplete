package com.example.suggestions

import android.content.Context
import android.os.Build
import android.os.LocaleList
import android.view.inputmethod.InputMethodManager
import java.util.Locale

data class KeyboardLanguage(val code: String, val label: String, val shortLabel: String, val assetName: String) {
    companion object {
        val AVAILABLE = listOf(
            KeyboardLanguage("pt-BR", "Português", "PT", "pt-BR"),
            KeyboardLanguage("en", "English", "EN", "en"),
            KeyboardLanguage("es", "Español", "ES", "es")
        )

        fun normalize(localeTag: String?): String {
            val language = localeTag.orEmpty().replace('_', '-').substringBefore('-').lowercase(Locale.ROOT)
            return when (language) {
                "en" -> "en"
                "es" -> "es"
                "pt" -> "pt-BR"
                else -> "pt-BR"
            }
        }
    }
}

/** The active typing-language order belongs to Revisa, independently of IME subtypes. */
class KeyboardLanguagePreferences(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("keyboard_languages", Context.MODE_PRIVATE)

    val activeLanguages: List<KeyboardLanguage>
        get() {
            ensureInitialized(null)
            val codes = prefs.getString(KEY_ACTIVE, null).orEmpty().split(',').filter(String::isNotBlank)
            return codes.mapNotNull(::find).ifEmpty { listOf(KeyboardLanguage.AVAILABLE.first()) }
        }

    val currentLanguage: KeyboardLanguage
        get() {
            ensureInitialized(null)
            val active = activeLanguages
            return find(prefs.getString(KEY_CURRENT, null))?.takeIf { selected -> active.any { it.code == selected.code } }
                ?: active.first()
        }

    /** Call once at startup; the Android locale/subtype seeds Revisa only on first use. */
    fun ensureInitialized(initialLanguageCode: String?) {
        if (prefs.getBoolean(KEY_INITIALIZED, false)) return
        val initial = KeyboardLanguage.normalize(initialLanguageCode)
        prefs.edit()
            .putString(KEY_ACTIVE, initial)
            .putString(KEY_CURRENT, initial)
            .putBoolean(KEY_INITIALIZED, true)
            .apply()
    }

    fun setEnabled(code: String, enabled: Boolean): Boolean {
        val language = find(code) ?: return false
        val active = activeLanguages.toMutableList()
        val existingIndex = active.indexOfFirst { it.code == language.code }
        if (enabled && existingIndex < 0) active += language
        if (!enabled && existingIndex >= 0) {
            if (active.size == 1) return false
            active.removeAt(existingIndex)
        }
        val current = currentLanguage
        val nextCurrent = if (active.any { it.code == current.code }) current else active.first()
        persist(active, nextCurrent)
        return true
    }

    fun cycle(): KeyboardLanguage {
        val active = activeLanguages
        val currentIndex = active.indexOfFirst { it.code == currentLanguage.code }.coerceAtLeast(0)
        val next = active[(currentIndex + 1) % active.size]
        persist(active, next)
        return next
    }

    fun select(code: String): Boolean {
        val language = find(code) ?: return false
        val active = activeLanguages
        if (active.none { it.code == language.code }) return false
        persist(active, language)
        return true
    }

    private fun persist(active: List<KeyboardLanguage>, current: KeyboardLanguage) {
        prefs.edit().putString(KEY_ACTIVE, active.joinToString(",") { it.code })
            .putString(KEY_CURRENT, current.code).apply()
    }

    private fun find(code: String?): KeyboardLanguage? = KeyboardLanguage.AVAILABLE.find { it.code == code }

    companion object {
        private const val KEY_ACTIVE = "active"
        private const val KEY_CURRENT = "current"
        private const val KEY_INITIALIZED = "initialized"

        fun systemLanguage(context: Context): String {
            val imm = context.getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
            val subtypeLocale = if (Build.VERSION.SDK_INT >= 28) runCatching {
                imm?.lastInputMethodSubtype?.locale
            }.getOrNull() else null
            val systemLocale = if (Build.VERSION.SDK_INT >= 24) {
                LocaleList.getDefault().takeIf { !it.isEmpty }?.get(0)?.toLanguageTag()
                    ?: Locale.getDefault().toLanguageTag()
            } else Locale.getDefault().toLanguageTag()
            return subtypeLocale?.takeIf(String::isNotBlank) ?: systemLocale
        }
    }
}

