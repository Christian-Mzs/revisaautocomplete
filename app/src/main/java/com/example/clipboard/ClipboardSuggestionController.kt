package com.example.clipboard

import android.content.ClipDescription
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import android.util.Size
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputConnection
import androidx.core.view.inputmethod.EditorInfoCompat
import androidx.core.view.inputmethod.InputConnectionCompat
import androidx.core.view.inputmethod.InputContentInfoCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

sealed class ClipboardSuggestion {
    abstract val identity: String
    data class Text(override val identity: String, val text: String) : ClipboardSuggestion()
    data class Image(override val identity: String, val uri: Uri, val mime: String,
                     val thumbnail: Bitmap? = null) : ClipboardSuggestion()
}

/** Session-only clipboard UI. No preferences, history writes, bitmaps or URI persistence. */
class ClipboardSuggestionController(
    private val context: Context,
    private val scope: CoroutineScope,
    private val onChanged: () -> Unit,
    private val probeImage: suspend (Uri, List<String>) -> String? = { uri, declared ->
        withContext(Dispatchers.IO) {
            runCatching {
                val mime = context.contentResolver.getType(uri)
                    ?: declared.firstOrNull { it.startsWith("image/") && !it.contains('*') }
                if (mime == null || !mime.startsWith("image/") || mime.contains('*')) null
                else context.contentResolver.openAssetFileDescriptor(uri, "r")?.use { mime }
            }.getOrNull()
        }
    },
    private val loadPreview: suspend (Uri) -> Bitmap? = { uri ->
        withContext(Dispatchers.IO) {
            runCatching {
                if (Build.VERSION.SDK_INT >= 29) context.contentResolver.loadThumbnail(uri, Size(64, 64), null)
                else {
                    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                    context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
                    if (bounds.outWidth <= 0 || bounds.outHeight <= 0) null else {
                        var sample = 1
                        while (maxOf(bounds.outWidth, bounds.outHeight) / sample > 128) sample *= 2
                        context.contentResolver.openInputStream(uri)?.use {
                            BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample })
                        }
                    }
                }
            }.getOrNull()
        }
    }
) {
    private val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    var suggestion: ClipboardSuggestion? = null
        private set
    private var identity: String? = null
    private var dismissed: String? = null
    private var revision = 0L
    private var job: Job? = null
    private var editor: EditorInfo? = null
    private var connection: InputConnection? = null
    private var sensitive = true
    private val legacyGrants = mutableSetOf<Pair<String, Uri>>()

    fun updateEditor(ic: InputConnection?, info: EditorInfo?, isSensitive: Boolean) {
        releaseLegacyGrants()
        connection = ic; editor = info; sensitive = isSensitive
        refresh()
    }

    fun clipboardChanged() {
        revision++
        identity = null; dismissed = null
        refresh()
    }

    fun refresh(revalidateImage: Boolean = true) {
        job?.cancel()
        if (sensitive || connection == null || editor == null) { publish(null); return }
        val clip = runCatching { clipboard.primaryClip }.getOrNull()
        if (clip == null || clip.itemCount == 0 ||
            clip.description.extras?.getBoolean("android.content.extra.IS_SENSITIVE", false) == true) {
            publish(null); return
        }
        val item = clip.getItemAt(0)
        val types = (0 until clip.description.mimeTypeCount).map { clip.description.getMimeType(it) }
        val stamp = if (Build.VERSION.SDK_INT >= 26) clip.description.timestamp else 0L
        val key = "$revision:$stamp:${item.text}:${item.uri}:${types.joinToString()}"
        if (key != identity) { identity = key; dismissed = null }
        if (dismissed == key) { publish(null); return }
        // A URI or image description always takes priority over an incidental text label.
        if (item.uri != null || types.any { it.startsWith("image/") }) {
            val uri = item.uri
            val info = editor ?: return
            val supported = EditorInfoCompat.getContentMimeTypes(info)
            if (uri == null || uri.scheme != "content" || supported.none {
                    it == "*/*" || it.startsWith("image/") }) { publish(null); return }
            val cached = suggestion as? ClipboardSuggestion.Image
            if (!revalidateImage && cached?.identity == key && supports(cached.mime, info)) return
            publish(null)
            job = scope.launch {
                val mime = probeImage(uri, types)
                if (identity != key || dismissed == key || sensitive || editor !== info) return@launch
                if (mime == null || !supports(mime, info)) return@launch
                if (cached?.identity == key && cached.mime == mime) {
                    publish(cached)
                    return@launch
                }
                publish(ClipboardSuggestion.Image(key, uri, mime))
                val preview = loadPreview(uri)
                if (identity == key && dismissed != key && !sensitive && editor === info) {
                    publish(ClipboardSuggestion.Image(key, uri, mime, preview))
                }
            }
        } else {
            val text = item.text?.toString()
            publish(if (text.isNullOrBlank()) null else ClipboardSuggestion.Text(key, text))
        }
    }

    private fun supports(mime: String, info: EditorInfo) =
        EditorInfoCompat.getContentMimeTypes(info).any { ClipDescription.compareMimeTypes(mime, it) }

    fun dismiss() {
        dismissed = identity
        job?.cancel()
        publish(null)
    }

    /** Re-read current clip and editor capabilities before committing; never paste stale content. */
    fun paste(): Boolean {
        if (sensitive) return false
        val original = suggestion ?: return false
        refresh(revalidateImage = false)
        if (suggestion?.identity != original.identity) return false
        val ic = connection ?: return false
        val info = editor ?: return false
        val success = runCatching {
            when (original) {
                is ClipboardSuggestion.Text -> ic.commitText(original.text, 1)
                is ClipboardSuggestion.Image -> {
                    if (!supports(original.mime, info)) return false
                    val content = InputContentInfoCompat(original.uri,
                        ClipDescription("Imagem copiada", arrayOf(original.mime)), null)
                    var flags = 0
                    if (Build.VERSION.SDK_INT >= 25) {
                        flags = InputConnectionCompat.INPUT_CONTENT_GRANT_READ_URI_PERMISSION
                    } else {
                        val pkg = info.packageName ?: return false
                        context.grantUriPermission(pkg, original.uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        legacyGrants.add(pkg to original.uri)
                    }
                    InputConnectionCompat.commitContent(ic, info, content, flags, null)
                }
            }
        }.getOrDefault(false)
        if (success) dismiss() else {
            releaseLegacyGrants()
            // Re-probe after a failed image insertion (including revoked URI access).
            if (original is ClipboardSuggestion.Image) { publish(null); refresh() }
        }
        return success
    }

    fun stop() {
        job?.cancel(); connection = null; editor = null; sensitive = true
        publish(null); releaseLegacyGrants()
    }

    private fun releaseLegacyGrants() {
        legacyGrants.forEach { (_, uri) ->
            runCatching { context.revokeUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION) }
        }
        legacyGrants.clear()
    }

    private fun publish(value: ClipboardSuggestion?) {
        if (suggestion != value) { suggestion = value; onChanged() }
    }
}
