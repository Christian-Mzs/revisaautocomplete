package com.example.clipboard

import android.content.ClipboardManager
import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

data class ClipboardEntry(val text: String, val pinned: Boolean = false)

/** Pinned order is stable; regular items are newest-first, capped at 30.
 * Pins have no separate automatic eviction limit. Old string arrays migrate in place.
 */
class ClipboardHistory(context: Context) {
    companion object { const val NORMAL_LIMIT = 30 }
    private val prefs = context.applicationContext.getSharedPreferences("clipboard_history", Context.MODE_PRIVATE)
    private val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    private var items: List<ClipboardEntry> = load()

    private fun load(): List<ClipboardEntry> {
        val parsed = runCatching {
            val array = JSONArray(prefs.getString("items", "[]"))
            (0 until array.length()).mapNotNull { index ->
                when (val value = array.opt(index)) {
                    is String -> ClipboardEntry(value)
                    is JSONObject -> value.optString("text").takeIf { it.isNotBlank() }
                        ?.let { ClipboardEntry(it, value.optBoolean("pinned", false)) }
                    else -> null
                }
            }.filter { it.text.isNotBlank() }.distinctBy { it.text }
        }.getOrDefault(emptyList())
        return bounded(parsed)
    }

    private fun bounded(entries: List<ClipboardEntry>) =
        entries.filter { it.pinned } + entries.filterNot { it.pinned }.take(NORMAL_LIMIT)

    fun records(): List<ClipboardEntry> = items.toList()
    // Retained text-only convenience API for existing consumers.
    fun entries(): List<String> = items.map { it.text }

    private fun save(entries: List<ClipboardEntry>) {
        val next = bounded(entries)
        if (items == next) return
        items = next
        val array = JSONArray()
        items.forEach { array.put(JSONObject().put("text", it.text).put("pinned", it.pinned)) }
        prefs.edit().putString("items", array.toString()).apply()
    }

    fun add(text: String) {
        if (text.isBlank()) return
        val existing = items.firstOrNull { it.text == text }
        if (existing?.pinned == true) return // Recopy must not reorder or unpin a fixed card.
        save(items.filter { it.pinned } + ClipboardEntry(text) + items.filter { !it.pinned && it.text != text })
    }

    fun setPinned(texts: Collection<String>, pinned: Boolean) {
        val selected = texts.toSet()
        save(items.map { if (it.text in selected) it.copy(pinned = pinned) else it })
    }

    fun removeAll(texts: Collection<String>) {
        val selected = texts.toSet()
        save(items.filterNot { it.text in selected })
    }

    fun remove(text: String) = removeAll(listOf(text))

    fun capture() {
        runCatching { clipboard.primaryClip?.plainTextOnly()?.let(::add) }
    }

    fun start(listener: ClipboardManager.OnPrimaryClipChangedListener) = clipboard.addPrimaryClipChangedListener(listener)
    fun stop(listener: ClipboardManager.OnPrimaryClipChangedListener) = clipboard.removePrimaryClipChangedListener(listener)
}
