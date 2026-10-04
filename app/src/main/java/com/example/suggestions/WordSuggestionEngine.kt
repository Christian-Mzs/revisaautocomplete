package com.example.suggestions

import android.content.Context
import morfologik.speller.Speller
import morfologik.stemming.Dictionary
import morfologik.stemming.DictionaryIterator
import java.text.Normalizer
import java.util.Locale

/** Offline word-prefix completion and one-word spelling correction, isolated from IME editing. */
class WordSuggestionEngine(private val context: Context) {
    private data class Entry(val folded: String, val word: String)
    private data class Loaded(val language: String, val entries: List<Entry>, val speller: Speller)

    @Volatile private var loaded: Loaded? = null

    fun suggest(languageCode: String, input: String, limit: Int = 3): List<String> {
        if (input.length < 2 || input.any { !it.isLetter() } || limit <= 0) return emptyList()
        val language = KeyboardLanguage.AVAILABLE.firstOrNull { it.code == languageCode } ?: return emptyList()
        val data = loaded?.takeIf { it.language == language.code } ?: synchronized(this) {
            loaded?.takeIf { it.language == language.code } ?: load(language).also { loaded = it }
        }
        val query = fold(input)
        val suggestions = LinkedHashSet<String>()

        // Prefix completions come first. The index is sorted, so lookup uses binary search.
        var index = lowerBound(data.entries, query)
        while (index < data.entries.size && data.entries[index].folded.startsWith(query) && suggestions.size < limit) {
            val candidate = data.entries[index++].word
            if (!candidate.equals(input, ignoreCase = true)) suggestions += matchCase(input, candidate)
        }

        // If the prefix yielded fewer than three choices, use the FSA speller for typo candidates.
        if (suggestions.size < limit) {
            data.speller.findSimilarWords(input.lowercase(Locale.ROOT)).forEach { candidate ->
                val cleaned = candidate.substringBefore('+').substringBefore('_')
                if (cleaned.isNotBlank() && !cleaned.equals(input, ignoreCase = true)) {
                    suggestions += matchCase(input, cleaned)
                }
            }
        }
        return suggestions.take(limit)
    }

    private fun load(language: KeyboardLanguage): Loaded {
        val dictionary = context.assets.open("suggestions/${language.assetName}.dict").use { dict ->
            context.assets.open("suggestions/${language.assetName}.info").use { info -> Dictionary.read(dict, info) }
        }
        val entries = ArrayList<Entry>()
        val iterator = DictionaryIterator(dictionary, Charsets.UTF_8.newDecoder(), true)
        while (iterator.hasNext()) {
            val word = iterator.next().word.toString()
            if (word.isNotBlank() && word.all(Char::isLetter)) entries += Entry(fold(word), word)
        }
        entries.sortWith(compareBy<Entry>({ it.folded }, { it.word.length }, { it.word }))
        return Loaded(language.code, entries, Speller(dictionary, 2))
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

    private fun matchCase(input: String, candidate: String): String =
        if (input.firstOrNull()?.isUpperCase() == true) candidate.replaceFirstChar { it.titlecase(Locale.ROOT) } else candidate

    private fun fold(value: String): String = Normalizer.normalize(value.lowercase(Locale.ROOT), Normalizer.Form.NFD)
        .filterNot { Character.getType(it) == Character.NON_SPACING_MARK.toInt() }
        .let { Normalizer.normalize(it, Normalizer.Form.NFC) }
}
