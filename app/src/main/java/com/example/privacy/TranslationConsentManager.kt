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

    fun isDiagnosticModeEnabled(): Boolean {
        return prefs.getBoolean(KEY_DIAGNOSTIC_MODE, false)
    }

    fun setDiagnosticModeEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_DIAGNOSTIC_MODE, enabled).apply()
    }

    companion object {
        private const val PREFS_NAME = "keyboard_privacy_settings"
        private const val KEY_CONSENT_ACCEPTED = "consent_accepted_google_translate"
        private const val KEY_DIAGNOSTIC_MODE = "diagnostic_mode_enabled"

        @Volatile
        private var instance: TranslationConsentManager? = null

        fun getInstance(context: Context): TranslationConsentManager {
            return instance ?: synchronized(this) {
                instance ?: TranslationConsentManager(context).also { instance = it }
            }
        }
    }
}
