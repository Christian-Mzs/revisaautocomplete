package com.example.clipboard

import android.content.ClipboardManager
import android.content.Context
import android.os.Build
import android.os.PersistableBundle
import org.json.JSONArray

/** Text-only history, newest first, bounded independently of the system clipboard. */
class ClipboardHistory(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("clipboard_history", Context.MODE_PRIVATE)
    private val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    private var items = runCatching {
        val array = JSONArray(prefs.getString("items", "[]"))
        (0 until array.length()).map { array.getString(it) }.distinct().take(30)
    }.getOrDefault(emptyList())

    fun entries(): List<String> = items.toList()

    fun add(text: String) {
        if (text.isBlank()) return
        val updated = (listOf(text) + items).distinct().take(30)
        if (updated == items) return
        items = updated
        prefs.edit().putString("items", JSONArray(items).toString()).apply()
    }

    fun capture() {
        runCatching {
            val clip = clipboard.primaryClip ?: return
            val extras: PersistableBundle? = if (Build.VERSION.SDK_INT >= 24) clip.description.extras else null
            if (extras?.getBoolean("android.content.extra.IS_SENSITIVE", false) == true) return
            // Do not resolve URIs or load external content.
            clip.getItemAt(0).text?.toString()?.let(::add)
        }
    }

    fun remove(text: String) {
        items = items.filterNot { it == text }
        prefs.edit().putString("items", JSONArray(items).toString()).apply()
    }

    fun start(listener: ClipboardManager.OnPrimaryClipChangedListener) =
        clipboard.addPrimaryClipChangedListener(listener)

    fun stop(listener: ClipboardManager.OnPrimaryClipChangedListener) =
        clipboard.removePrimaryClipChangedListener(listener)
}
