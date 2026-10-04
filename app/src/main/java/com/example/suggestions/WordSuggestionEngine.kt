package com.example.suggestions

import android.content.Context
import java.text.Normalizer
import java.util.Locale

/** Offline prefix completion and bounded edit-distance correction, isolated from IME editing. */
class WordSuggestionEngine(private val context: Context) {
    private data class Entry(val folded: String, val word: String)
    private data class Loaded(
        val language: String,
        val entries: List<Entry>,
        val byLength: Map<Int, List<Entry>>
    )

    @Volatile private var loaded: Loaded? = null

    fun suggest(languageCode: String, input: String, limit: Int = 3): List<String> {
        if (input.length < 2 || input.any { !it.isLetter() } || limit <= 0) return emptyList()
        val language = KeyboardLanguage.AVAILABLE.firstOrNull { it.code == languageCode } ?: return emptyList()
        val data = loaded?.takeIf { it.language == language.code } ?: synchronized(this) {
            loaded?.takeIf { it.language == language.code } ?: load(language).also { loaded = it }
        }
        val query = fold(input)
        val suggestions = LinkedHashSet<String>()

        // Prefix completions come first, with shorter forms ranked ahead of longer ones.
        val prefixMatches = ArrayList<Entry>()
        var index = lowerBound(data.entries, query)
        while (index < data.entries.size && data.entries[index].folded.startsWith(query) && prefixMatches.size < PREFIX_SCAN_LIMIT) {
            val candidate = data.entries[index++]
            if (!candidate.word.equals(input, ignoreCase = true)) prefixMatches += candidate
        }
        prefixMatches.sortedWith(compareBy<Entry>({ it.word.length }, { it.folded }, { it.word }))
            .take(limit).forEach { suggestions += matchCase(input, it.word) }

        // Use a small, language-neutral edit distance only when prefix completion has gaps.
        if (suggestions.size < limit && input.length >= MIN_CORRECTION_LENGTH) {
            val corrections = ArrayList<Correction>()
            for (length in (query.length - MAX_EDIT_DISTANCE).coerceAtLeast(1)..(query.length + MAX_EDIT_DISTANCE)) {
                for (candidate in data.byLength[length].orEmpty()) {
                    if (candidate.folded.length < MIN_CORRECTION_LENGTH ||
                        candidate.word.equals(input, ignoreCase = true) || candidate.folded == query) continue
                    val distance = optimalStringAlignment(query, candidate.folded, MAX_EDIT_DISTANCE)
                    if (distance in 1..MAX_EDIT_DISTANCE) {
                        val preservesOrder = isSubsequence(query, candidate.folded) || isSubsequence(candidate.folded, query)
                        corrections += Correction(candidate, distance, preservesOrder)
                    }
                }
            }
            corrections.sortedWith(compareBy<Correction>(
                { it.distance }, { !it.preservesOrder }, { commonPrefixLength(query, it.entry.folded) * -1 },
                { it.entry.word.length }, { it.entry.word.lowercase(Locale.ROOT) }
            ))
                .forEach { suggestions += matchCase(input, it.entry.word) }
        }
        return suggestions.take(limit)
    }

    private data class Correction(val entry: Entry, val distance: Int, val preservesOrder: Boolean)

    private fun isSubsequence(shorter: String, longer: String): Boolean {
        if (shorter.length > longer.length) return false
        var index = 0
        for (char in longer) if (index < shorter.length && char == shorter[index]) index++
        return index == shorter.length
    }

    private fun load(language: KeyboardLanguage): Loaded {
        val entries = ArrayList<Entry>()
        context.assets.open("suggestions/${language.assetName}.txt").bufferedReader(Charsets.UTF_8).useLines { lines ->
            lines.forEach { line ->
                val word = line.trim()
                if (word.length in 2..MAX_WORD_LENGTH && word.all(Char::isLetter)) {
                    entries += Entry(fold(word), word)
                }
            }
        }
        entries.sortWith(compareBy<Entry>({ it.folded }, { it.word.length }, { it.word }))
        val byLength = HashMap<Int, MutableList<Entry>>()
        entries.forEach { entry -> byLength.getOrPut(entry.folded.length) { ArrayList() } += entry
        }
        return Loaded(language.code, entries, byLength)
    }

    private fun lowerBound(entries: List<Entry>, query: String): Int {
        var low = 0
        var high = entries.size
        while (low < high) {
            val middle = (low + high) ushr 1
            if (entries[middle].folded < query) low = middle + 1 else high = middle
        }
        return low
    }

    private fun optimalStringAlignment(left: String, right: String, cutoff: Int): Int {
        if (kotlin.math.abs(left.length - right.length) > cutoff) return cutoff + 1
        var previousPrevious = IntArray(right.length + 1) { it }
        var previous = previousPrevious.copyOf()
        var current = IntArray(right.length + 1)
        for (i in 1..left.length) {
            current[0] = i
            var rowMinimum = current[0]
            for (j in 1..right.length) {
                current[j] = minOf(
                    previous[j] + 1,
                    current[j - 1] + 1,
                    previous[j - 1] + if (left[i - 1] == right[j - 1]) 0 else 1
                )
                if (i > 1 && j > 1 && left[i - 1] == right[j - 2] && left[i - 2] == right[j - 1]) {
                    current[j] = minOf(current[j], previousPrevious[j - 2] + 1)
                }
                rowMinimum = minOf(rowMinimum, current[j])
            }
            if (rowMinimum > cutoff) return cutoff + 1
            val recycled = previousPrevious
            previousPrevious = previous
            previous = current
            current = recycled
        }
        return previous[right.length]
    }

    private fun commonPrefixLength(a: String, b: String): Int {
        var length = 0
        while (length < a.length && length < b.length && a[length] == b[length]) length++
        return length
    }

    private fun matchCase(input: String, candidate: String): String =
        if (input.firstOrNull()?.isUpperCase() == true) candidate.replaceFirstChar { it.titlecase(Locale.ROOT) } else candidate

    private fun fold(value: String): String = Normalizer.normalize(value.lowercase(Locale.ROOT), Normalizer.Form.NFD)
        .filterNot { Character.getType(it) == Character.NON_SPACING_MARK.toInt() }
        .let { Normalizer.normalize(it, Normalizer.Form.NFC) }

    companion object {
        private const val PREFIX_SCAN_LIMIT = 256
        private const val MAX_EDIT_DISTANCE = 2
        private const val MIN_CORRECTION_LENGTH = 3
        private const val MAX_WORD_LENGTH = 24
    }
}

