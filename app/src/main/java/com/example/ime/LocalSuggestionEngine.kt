package com.example.ime

import java.text.Normalizer
import java.util.Locale
import java.io.InputStream

/** Offline VERO vocabulary plus a small priority table for common accent ambiguities. */
object LocalSuggestionEngine {
    @Volatile private var dictionary: OfflineDictionary? = null
    val isDictionaryReady: Boolean get() = dictionary != null

    @Synchronized
    fun loadDictionary(openAsset: () -> InputStream) {
        if (dictionary == null) dictionary = OfflineDictionary.read(openAsset())
    }

    private val locale = Locale.forLanguageTag("pt-BR")
    private val alternatives = mapOf(
        "nao" to listOf("não"), "voce" to listOf("você", "vocês"),
        "voces" to listOf("vocês"), "tambem" to listOf("também"),
        "so" to listOf("só"), "ja" to listOf("já"), "ate" to listOf("até"),
        "sera" to listOf("será"), "porque" to listOf("porque", "porquê"),
        "facil" to listOf("fácil"), "dificil" to listOf("difícil"),
        "esta" to listOf("esta", "está"), "estao" to listOf("estão"),
        "estamos" to listOf("estamos"), "ola" to listOf("olá"),
        "bom" to listOf("bom"), "dia" to listOf("dia"),
        "obrigado" to listOf("obrigado"), "obrigada" to listOf("obrigada"),
        "amanha" to listOf("amanhã"), "hoje" to listOf("hoje"),
        "otimo" to listOf("ótimo"), "possivel" to listOf("possível"),
        "entao" to listOf("então"), "acao" to listOf("ação")
    )

    fun suggest(word: String): List<String> {
        val normalized = OfflineDictionary.fold(word)
        // Preserve the existing, deliberate ordering for common accent corrections.
        if (normalized in alternatives) return suggestFallback(word)
        val candidates = dictionary?.suggest(word) ?: return suggestFallback(word)
        return (suggestFallback(word) + preserveCase(word, candidates)).distinct().take(3)
    }

    fun suggestFallback(word: String): List<String> {
        if (word.isBlank()) return emptyList()
        val lower = word.lowercase(locale)
        val normalized = Normalizer.normalize(lower, Normalizer.Form.NFD)
            .filter { Character.getType(it) != Character.NON_SPACING_MARK.toInt() }
        val exact = alternatives[normalized]
        val candidates = if (exact != null) {
            if (lower != normalized) listOf(lower) + exact else listOf(exact.first(), lower) + exact.drop(1)
        } else {
            if (normalized.length < 2) return emptyList()
            alternatives.asSequence().filter { it.key.startsWith(normalized) }
                .flatMap { it.value.asSequence() }.take(3).toList()
        }
        return preserveCase(word, candidates)
    }

    private fun preserveCase(word: String, candidates: List<String>): List<String> {
        if (word.isBlank()) return emptyList()
        return candidates.map { candidate ->
            when {
                word == word.uppercase(locale) -> candidate.uppercase(locale)
                word.first().isUpperCase() -> candidate.replaceFirstChar { it.titlecase(locale) }
                else -> candidate
            }
        }.distinct().take(3)
    }
}
