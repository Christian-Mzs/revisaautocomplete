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
    private val probeImage: (suspend (Uri, List<String>) -> String?)? = null,
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
    companion object { const val DIAGNOSTIC_TAG = "RevisaClipboardDiag" }
    // Temporary diagnostic build: release APKs never emit or display these diagnostics.
    var diagnosticSummary: String? = null
        private set
    private var diagnosticMetadata = ""

    private fun safeMime(value: String?): String = when {
        value == null -> "null"
        value.matches(Regex("[A-Za-z0-9!#$&^_.+*-]+/[A-Za-z0-9!#$&^_.+*-]+")) -> value.take(96)
        else -> "invalid"
    }

    private fun trace(reason: String, details: String = "", visible: Boolean = false) {
        if (!com.example.BuildConfig.DEBUG) return
        android.util.Log.d(DIAGNOSTIC_TAG, "$reason ${if (visible) diagnosticMetadata else ""} $details".trim())
        if (visible) {
            val next = "CLIP: $diagnosticMetadata${if (details.isEmpty()) "" else " $details"}\nreason=$reason"
            if (next != diagnosticSummary) { diagnosticSummary = next; onChanged() }
        }
    }

    private fun clearDiagnostic() {
        diagnosticMetadata = ""
        if (diagnosticSummary != null) { diagnosticSummary = null; onChanged() }
    }

    private data class Probe(val mime: String?, val resolver: String?, val opened: Boolean?, val reason: String)

    private suspend fun inspectImage(uri: Uri, declared: List<String>): Probe = withContext(Dispatchers.IO) {
        var resolver: String? = null
        var detected: String? = null
        var opened: Boolean? = null
        var stage = "RESOLVER"
        try {
            resolver = context.contentResolver.getType(uri)
            detected = resolver ?: declared.firstOrNull { it.startsWith("image/") && !it.contains('*') }
            if (detected == null) Probe(null, resolver, null, "IMAGE_SKIP_RESOLVER_MIME_NULL")
            else if (!detected.startsWith("image/") || detected.contains('*'))
                Probe(null, resolver, null, "IMAGE_SKIP_INVALID_MIME")
            else {
                stage = "OPEN"
                opened = context.contentResolver.openAssetFileDescriptor(uri, "r")?.use { true } ?: false
                Probe(if (opened == true) detected else null, resolver, opened,
                    if (opened == true) "IMAGE_PROBE_OK" else "IMAGE_SKIP_CANNOT_OPEN_URI")
            }
        } catch (_: Throwable) {
            // Never log exception messages: provider errors can contain a complete URI/path.
            Probe(null, resolver, opened,
                if (stage == "OPEN") "IMAGE_SKIP_CANNOT_OPEN_URI" else "IMAGE_SKIP_RESOLVER_ERROR")
        }
    }

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
        if (sensitive) { publish(null); clearDiagnostic(); trace("IMAGE_SKIP_SENSITIVE"); return }
        clearDiagnostic()
        if (connection == null) { trace("IMAGE_SKIP_NO_CONNECTION", visible = true); publish(null); return }
        if (editor == null) { trace("IMAGE_SKIP_NO_EDITOR", visible = true); publish(null); return }
        val infoForLog = editor!!
        val editorMimes = EditorInfoCompat.getContentMimeTypes(infoForLog)
        trace("EDITOR", "package=${infoForLog.packageName?.takeIf { it.matches(Regex("[A-Za-z0-9_.]+")) } ?: "unknown"} inputType=${infoForLog.inputType} MIME=${editorMimes.map(::safeMime)}")
        val clipResult = runCatching { clipboard.primaryClip }
        if (clipResult.isFailure) trace("CLIPBOARD_READ_FAILED")
        val clip = clipResult.getOrNull()
        if (clip == null) {
            diagnosticMetadata = "exists=N items=0 text=N uri=N scheme=null\nmime=[] imageCandidate=N editorMime=${editorMimes.map(::safeMime)}"
            trace(if (clipResult.isFailure) "CLIPBOARD_READ_FAILED" else "NO_PRIMARY_CLIP", visible = true)
            publish(null); return
        }
        val markedSensitive = clip.description.extras?.getBoolean("android.content.extra.IS_SENSITIVE", false) == true
        if (markedSensitive) { trace("IMAGE_SKIP_SENSITIVE_CLIP"); publish(null); return }
        if (clip.itemCount == 0) {
            diagnosticMetadata = "exists=Y items=0 text=N uri=N scheme=null\nmime=[] imageCandidate=N editorMime=${editorMimes.map(::safeMime)}"
            trace("CLIPBOARD_EMPTY", visible = true); publish(null); return
        }
        val item = clip.getItemAt(0)
        val types = (0 until clip.description.mimeTypeCount).map { clip.description.getMimeType(it) }
        val stamp = if (Build.VERSION.SDK_INT >= 26) clip.description.timestamp else 0L
        val scheme = when (item.uri?.scheme) { null -> "null"; "content" -> "content"; "file" -> "file"; else -> "other" }
        trace("CLIPBOARD", "primaryClip=true itemCount=${clip.itemCount} text=${item.text != null} uri=${item.uri != null} scheme=$scheme CLIP=${types.map(::safeMime)} timestampAvailable=${Build.VERSION.SDK_INT >= 26} IS_SENSITIVE=false")
        val looksLikeImage = item.uri != null || types.any { it.startsWith("image/") }
        diagnosticMetadata = "exists=Y items=${clip.itemCount} text=${if (item.text != null) "Y" else "N"} uri=${if (item.uri != null) "Y" else "N"} scheme=$scheme\nmime=${types.map(::safeMime)} imageCandidate=${if (looksLikeImage) "Y" else "N"} editorMime=${editorMimes.map(::safeMime)}"
        trace("CLIPBOARD_RECEIVED", visible = true)
        val key = "$revision:$stamp:${item.text}:${item.uri}:${types.joinToString()}"
        if (key != identity) { identity = key; dismissed = null }
        if (dismissed == key) { trace(if (looksLikeImage) "IMAGE_SKIP_DISMISSED" else "CLIPBOARD_NOT_IMAGE_DISMISSED", visible = true); publish(null); return }
        // A URI or image description always takes priority over an incidental text label.
        if (item.uri != null || types.any { it.startsWith("image/") }) {
            val uri = item.uri
            val info = editor ?: run { trace("IMAGE_SKIP_NO_EDITOR", visible = true); return }
            val supported = EditorInfoCompat.getContentMimeTypes(info)
            if (uri == null) { trace("IMAGE_SKIP_NO_URI", visible = true); publish(null); return }
            if (uri.scheme != "content") { trace("IMAGE_SKIP_NON_CONTENT_URI", visible = true); publish(null); return }
            val imageCapable = supported.any { it == "*/*" || it.startsWith("image/") }
            trace("EDITOR_IMAGE_CAPABILITY", "imageMime=$imageCapable", visible = true)
            if (!imageCapable) { trace("IMAGE_SKIP_EDITOR_NO_IMAGE_MIME", "OPEN=NOT_ATTEMPTED", visible = true); publish(null); return }
            val cached = suggestion as? ClipboardSuggestion.Image
            if (!revalidateImage && cached?.identity == key && supports(cached.mime, info)) {
                trace("IMAGE_READY_CACHED", visible = true); return
            }
            publish(null)
            job = scope.launch {
                val result = if (probeImage == null) inspectImage(uri, types)
                    else probeImage.invoke(uri, types).let { Probe(it, null, null,
                        if (it == null) "IMAGE_SKIP_PROBE_FAILED" else "IMAGE_PROBE_OK") }
                if (identity != key || dismissed == key || sensitive || editor !== info) {
                    if (!sensitive) trace("IMAGE_SKIP_STALE_REQUEST")
                    return@launch
                }
                val mime = result.mime
                diagnosticMetadata += " RESOLVER=${safeMime(result.resolver)} DETECTED=${safeMime(mime)} OPEN=${result.opened ?: "NOT_ATTEMPTED"}"
                trace(result.reason, visible = true)
                if (mime == null) return@launch
                val compatible = supports(mime, info)
                diagnosticMetadata += " supports=$compatible"
                trace("IMAGE_MIME_MATCH", "MIME=${safeMime(mime)}", visible = true)
                if (!compatible) { trace("IMAGE_SKIP_MIME_MISMATCH", visible = true); return@launch }
                if (cached?.identity == key && cached.mime == mime) {
                    trace("IMAGE_READY_CACHED", visible = true)
                    publish(cached)
                    return@launch
                }
                trace("IMAGE_READY", visible = true)
                publish(ClipboardSuggestion.Image(key, uri, mime))
                val preview = loadPreview(uri)
                if (identity == key && dismissed != key && !sensitive && editor === info) {
                    trace(if (preview != null) "IMAGE_THUMBNAIL_OK" else "IMAGE_THUMBNAIL_FAILED", visible = true)
                    publish(ClipboardSuggestion.Image(key, uri, mime, preview))
                } else if (!sensitive) trace("IMAGE_SKIP_STALE_PREVIEW")
            }
        } else {
            trace("CLIPBOARD_NOT_IMAGE", visible = true)
            val text = item.text?.toString()
            publish(if (text.isNullOrBlank()) null else ClipboardSuggestion.Text(key, text))
        }
    }

    private fun supports(mime: String, info: EditorInfo) =
        EditorInfoCompat.getContentMimeTypes(info).any { ClipDescription.compareMimeTypes(mime, it) }

    fun dismiss() {
        trace("SUGGESTION_DISMISSED")
        clearDiagnostic()
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
                    if (!supports(original.mime, info)) { trace("IMAGE_COMMIT_SKIP_MIME_MISMATCH"); return false }
                    val content = InputContentInfoCompat(original.uri,
                        ClipDescription("Imagem copiada", arrayOf(original.mime)), null)
                    var flags = 0
                    if (Build.VERSION.SDK_INT >= 25) {
                        flags = InputConnectionCompat.INPUT_CONTENT_GRANT_READ_URI_PERMISSION
                    } else {
                        val pkg = info.packageName ?: run { trace("IMAGE_COMMIT_SKIP_NO_PACKAGE"); return false }
                        context.grantUriPermission(pkg, original.uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        legacyGrants.add(pkg to original.uri)
                    }
                    InputConnectionCompat.commitContent(ic, info, content, flags, null)
                }
            }
        }.getOrDefault(false)
        if (original is ClipboardSuggestion.Image) trace(if (success) "IMAGE_COMMIT_OK" else "IMAGE_COMMIT_FAILED")
        if (success) dismiss() else {
            releaseLegacyGrants()
            // Re-probe after a failed image insertion (including revoked URI access).
            if (original is ClipboardSuggestion.Image) { publish(null); refresh() }
        }
        return success
    }

    fun stop() {
        clearDiagnostic()
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
