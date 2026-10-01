package com.example.clipboard

import android.content.ClipData

/** No coercion: URIs, intents, mixed media and sensitive clips are never text history/suggestions. */
internal fun ClipData.plainTextOnly(): String? {
    if (description.extras?.getBoolean("android.content.extra.IS_SENSITIVE", false) == true) return null
    if (itemCount == 0 || description.mimeTypeCount == 0) return null
    if ((0 until description.mimeTypeCount).any { !description.getMimeType(it).startsWith("text/") }) return null
    val item = getItemAt(0)
    if (item.uri != null || item.intent != null) return null
    return item.text?.toString()
}
