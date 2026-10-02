package com.example.settings

import android.content.Context

/** Presentation state is separate from the account, runtime, IME and language preferences. */
class OnboardingPreferences(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("revisa_presentation", Context.MODE_PRIVATE)
    val completed: Boolean get() = prefs.getBoolean("completed", false)
    fun complete() { prefs.edit().putBoolean("completed", true).apply() }
}
