package com.example.ime

import java.util.regex.Pattern

object GraphemeBackspace {
    private val clusters = Pattern.compile("\\X")

    fun deletionLength(beforeCursor: String): Int {
        if (beforeCursor.isEmpty()) return 0
        if (beforeCursor.last().code < 128) return 1
        val matcher = clusters.matcher(beforeCursor)
        var start = beforeCursor.lastIndex
        while (matcher.find()) start = matcher.start()
        return beforeCursor.length - start
    }
}
