package com.example.clipboard

import android.content.ClipData
import android.os.Build
import java.security.MessageDigest

/** No coercion: URIs, intents, mixed media and sensitive clips are never text history/suggestions. */
internal fun ClipData.plainTextOnly(): String? {
    if (description.extras?.getBoolean("android.content.extra.IS_SENSITIVE", false) == true) return null
    if (itemCount == 0 || description.mimeTypeCount == 0) return null
    if ((0 until description.mimeTypeCount).any { !description.getMimeType(it).startsWith("text/") }) return null
    val item = getItemAt(0)
    if (item.uri != null || item.intent != null) return null
    return item.text?.toString()
}

/** Stable identity for de-duplicating clipboard notifications and recognizing new copies. */
internal fun ClipData.identityFingerprint(): String? {
    val text = plainTextOnly() ?: return null
    val timestamp = if (Build.VERSION.SDK_INT >= 26) description.timestamp else 0L
    return MessageDigest.getInstance("SHA-256")
        .digest("$timestamp:$text".toByteArray(Charsets.UTF_8))
        .joinToString("") { "%02x".format(it.toInt() and 0xff) }
}
