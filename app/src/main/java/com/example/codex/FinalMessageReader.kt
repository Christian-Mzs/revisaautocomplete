package com.example.codex

import java.io.File

internal object FinalMessageReader {
    fun read(work: File): String {
        val output = File(work,"result.txt")
        check(output.isFile && output.length() in 1..1_000_000) { "Codex não devolveu uma mensagem final válida." }
        return output.readText().trim().also { check(it.isNotBlank()) { "Resposta vazia." } }
    }
}
