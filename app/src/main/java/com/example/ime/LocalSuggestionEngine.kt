package com.example.ime

import java.text.Normalizer
import java.util.Locale

/** A small, offline vocabulary. It never changes editor text by itself. */
object LocalSuggestionEngine {
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
        return candidates.map { candidate ->
            when {
                word == word.uppercase(locale) -> candidate.uppercase(locale)
                word.first().isUpperCase() -> candidate.replaceFirstChar { it.titlecase(locale) }
                else -> candidate
            }
        }.distinct().take(3)
    }
}
