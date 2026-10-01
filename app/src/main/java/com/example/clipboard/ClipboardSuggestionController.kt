package com.example.clipboard

import android.content.ClipboardManager
import android.content.Context
import android.os.Build
import android.view.inputmethod.InputConnection
import java.security.MessageDigest

data class ClipboardSuggestion(val identity: String, val text: String)

/** Temporary text-only suggestion. Dismiss lasts until a clipboard change, not editor changes. */
class ClipboardSuggestionController(private val context: Context, private val onChanged: () -> Unit) {
    private val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    var suggestion: ClipboardSuggestion? = null
        private set
    private var identity: String? = null
    // Only a fingerprint is persisted; clipboard text is never stored here.
    private val dismissal = context.getSharedPreferences("clipboard_suggestion_dismissal", Context.MODE_PRIVATE)
    private var connection: InputConnection? = null
    private var sensitive = true

    fun updateEditor(ic: InputConnection?, isSensitive: Boolean) {
        connection = ic
        sensitive = isSensitive
        refresh()
    }

    fun clipboardChanged() {
        // Android can notify again when IME access resumes without a new copy.
        refresh()
    }

    fun refresh() {
        if (sensitive || connection == null) { publish(null); return }
        val clip = runCatching { clipboard.primaryClip }.getOrNull()
        val text = clip?.plainTextOnly()
        if (text.isNullOrBlank()) { publish(null); return }
        val stamp = if (Build.VERSION.SDK_INT >= 26) clip?.description?.timestamp ?: 0L else 0L
        val key = MessageDigest.getInstance("SHA-256")
            .digest("$stamp:$text".toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it.toInt() and 0xff) }
        identity = key
        publish(if (dismissal.getString("item", null) == key) null else ClipboardSuggestion(key, text))
    }

    fun dismiss() {
        identity?.let { dismissal.edit().putString("item", it).apply() }
        publish(null)
    }

    fun paste(): Boolean {
        if (sensitive) return false
        val previous = suggestion ?: return false
        refresh()
        val current = suggestion ?: return false
        if (current.identity != previous.identity) return false
        val accepted = runCatching { connection?.commitText(current.text, 1) == true }.getOrDefault(false)
        if (accepted) dismiss()
        return accepted
    }

    fun stop() {
        connection = null; sensitive = true
        publish(null)
    }

    private fun publish(value: ClipboardSuggestion?) {
        if (suggestion != value) { suggestion = value; onChanged() }
    }
}
