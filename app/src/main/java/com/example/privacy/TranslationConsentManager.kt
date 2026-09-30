package com.example.privacy

import android.content.Context
import android.content.SharedPreferences

class TranslationConsentManager(context: Context) {

    private val prefs: SharedPreferences =
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun hasAcceptedConsent(): Boolean {
        return prefs.getBoolean(KEY_CONSENT_ACCEPTED, false)
    }

    fun setConsentAccepted(accepted: Boolean) {
        prefs.edit().putBoolean(KEY_CONSENT_ACCEPTED, accepted).apply()
    }

    fun getLastTranslationLanguageCode(): String {
        return prefs.getString(KEY_LAST_TRANSLATION_LANG, "en") ?: "en"
    }

    fun setLastTranslationLanguageCode(code: String) {
        prefs.edit().putString(KEY_LAST_TRANSLATION_LANG, code).apply()
    }

    companion object {
        private const val PREFS_NAME = "revisa_preferences"
        private const val KEY_CONSENT_ACCEPTED = "consent_accepted_translation"
        private const val KEY_LAST_TRANSLATION_LANG = "last_translation_language"

        @Volatile
        private var instance: TranslationConsentManager? = null

        fun getInstance(context: Context): TranslationConsentManager {
            return instance ?: synchronized(this) {
                instance ?: TranslationConsentManager(context).also { instance = it }
            }
        }
    }
}
