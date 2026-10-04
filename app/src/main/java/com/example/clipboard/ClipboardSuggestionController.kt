package com.example.clipboard

import android.content.ClipboardManager
import android.content.Context
import android.view.inputmethod.InputConnection

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
    private var sessionActive = false
    private var lastObservedIdentity: String? = null
    private var eligibleIdentity: String? = null

    fun startSession() {
        sessionActive = true
        lastObservedIdentity = currentClipboardIdentity()
        eligibleIdentity = null
        identity = null
        publish(null)
    }

    fun updateEditor(ic: InputConnection?, isSensitive: Boolean) {
        connection = ic
        sensitive = isSensitive
        refresh()
    }

    /** Returns true only when the primary clip's identity has actually changed this session. */
    fun clipboardChanged(): Boolean {
        if (!sessionActive) return false
        val current = currentClipboardIdentity()
        if (current == lastObservedIdentity) return false
        lastObservedIdentity = current
        eligibleIdentity = current
        refresh()
        return true
    }

    fun refresh() {
        if (!sessionActive || sensitive || connection == null) { identity = null; publish(null); return }
        val clip = runCatching { clipboard.primaryClip }.getOrNull()
        val text = clip?.plainTextOnly()
        val key = clip?.identityFingerprint()
        if (text.isNullOrBlank() || key == null || key != eligibleIdentity) {
            identity = null
            publish(null)
            return
        }
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
        sessionActive = false
        lastObservedIdentity = null
        eligibleIdentity = null
        connection = null
        sensitive = true
        identity = null
        publish(null)
    }

    private fun currentClipboardIdentity(): String? =
        runCatching { clipboard.primaryClip?.identityFingerprint() }.getOrNull()

    private fun publish(value: ClipboardSuggestion?) {
        if (suggestion != value) { suggestion = value; onChanged() }
    }
}
