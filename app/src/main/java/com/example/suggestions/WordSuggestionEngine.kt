package com.example.suggestions

import android.content.Context
import java.text.Normalizer
import java.util.Locale

/** Offline frequency-ranked completion and bounded correction, isolated from IME editing. */
class WordSuggestionEngine(private val context: Context) {
    private data class Entry(val folded: String, val word: String, val frequency: Long)
    private data class Loaded(
        val language: String,
        val entries: List<Entry>,
        val byLength: Map<Int, List<Entry>>,
        val byPrefix: Map<String, List<Entry>>
    )

    @Volatile private var loaded: Loaded? = null

    fun suggest(languageCode: String, input: String, limit: Int = 3): List<String> {
        if (input.length < 2 || input.any { !it.isLetter() } || limit <= 0) return emptyList()
        val language = KeyboardLanguage.AVAILABLE.firstOrNull { it.code == languageCode } ?: return emptyList()
        val data = loaded?.takeIf { it.language == language.code } ?: synchronized(this) {
            loaded?.takeIf { it.language == language.code } ?: load(language).also { loaded = it }
        }
        val query = fold(input)

        // A common accentless form such as "nao" should resolve directly to its
        // more frequent correctly accented spelling, without filling the row
        // with unrelated words that happen to share the same folded prefix.
        val prefixCandidates = data.byPrefix[query.take(PREFIX_INDEX_LENGTH)].orEmpty()
        val foldedExact = prefixCandidates.firstOrNull { it.folded == query }
        if (foldedExact != null &&
            !foldedExact.word.equals(input, ignoreCase = true) &&
            diacriticCount(foldedExact.word) > diacriticCount(input)
        ) {
            return listOf(matchCase(input, foldedExact.word)).take(limit)
        }

        val suggestions = LinkedHashSet<String>()
        val seenFolded = HashSet<String>()

        // Entries are stored in descending source frequency, so the first three
        // prefix matches are the most common candidates for the active language.
        for (candidate in prefixCandidates) {
            if (!candidate.folded.startsWith(query) || candidate.folded == query) continue
            if (!seenFolded.add(candidate.folded)) continue
            suggestions += matchCase(input, candidate.word)
            if (suggestions.size == limit) break
        }

        // Fuzzy correction only fills an otherwise empty prefix result. This
        // avoids appending weak edit-distance guesses to useful completions.
        if (suggestions.isEmpty() && input.length >= MIN_CORRECTION_LENGTH) {
            val corrections = ArrayList<Correction>()
            for (length in (query.length - MAX_EDIT_DISTANCE).coerceAtLeast(1)..(query.length + MAX_EDIT_DISTANCE)) {
                for (candidate in data.byLength[length].orEmpty()) {
                    if (candidate.word.equals(input, ignoreCase = true) || candidate.folded == query) continue
                    val distance = optimalStringAlignment(query, candidate.folded, MAX_EDIT_DISTANCE)
                    if (distance in 1..MAX_EDIT_DISTANCE) {
                        val preservesOrder = isSubsequence(query, candidate.folded) || isSubsequence(candidate.folded, query)
                        corrections += Correction(candidate, distance, preservesOrder)
                    }
                }
            }
            corrections.sortedWith(compareBy<Correction>(
                { it.distance },
                { -it.entry.frequency },
                { !it.preservesOrder },
                { -commonPrefixLength(query, it.entry.folded) },
                { it.entry.word.length },
                { it.entry.word.lowercase(Locale.ROOT) }
            ))
                .distinctBy { it.entry.folded }
                .take(limit)
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
                val separator = line.lastIndexOf('\t')
                if (separator <= 0) return@forEach
                val word = line.substring(0, separator).trim()
                val frequency = line.substring(separator + 1).trim().toLongOrNull() ?: return@forEach
                if (word.length in 2..MAX_WORD_LENGTH && word.all(Char::isLetter) && frequency > 0) {
                    entries += Entry(fold(word), word, frequency)
                }
            }
        }
        entries.sortWith(
            compareByDescending<Entry> { it.frequency }
                .thenBy { it.folded }
                .thenBy { it.word }
        )
        val byLength = HashMap<Int, MutableList<Entry>>()
        val byPrefix = HashMap<String, MutableList<Entry>>()
        entries.forEach { entry ->
            byLength.getOrPut(entry.folded.length) { ArrayList() } += entry
            byPrefix.getOrPut(entry.folded.take(PREFIX_INDEX_LENGTH)) { ArrayList() } += entry
        }
        return Loaded(language.code, entries, byLength, byPrefix)
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

    private fun diacriticCount(value: String): Int =
        Normalizer.normalize(value, Normalizer.Form.NFD)
            .count { Character.getType(it) == Character.NON_SPACING_MARK.toInt() }

    private fun matchCase(input: String, candidate: String): String =
        if (input.firstOrNull()?.isUpperCase() == true) candidate.replaceFirstChar { it.titlecase(Locale.ROOT) } else candidate

    private fun fold(value: String): String = Normalizer.normalize(value.lowercase(Locale.ROOT), Normalizer.Form.NFD)
        .filterNot { Character.getType(it) == Character.NON_SPACING_MARK.toInt() }
        .let { Normalizer.normalize(it, Normalizer.Form.NFC) }

    companion object {
        private const val MAX_EDIT_DISTANCE = 2
        private const val MIN_CORRECTION_LENGTH = 3
        private const val MAX_WORD_LENGTH = 24
        private const val PREFIX_INDEX_LENGTH = 2
    }
}
