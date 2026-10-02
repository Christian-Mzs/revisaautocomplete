package com.example.codex

object LoginLinks {
    fun find(line: String): String? = Regex("https://[^\\s<>\"']+").findAll(line)
        .map { it.value.trimEnd('.',',',')') }.firstOrNull {
            // Only let the CLI open official authentication hosts, not arbitrary text URLs.
            val host = try { java.net.URI(it).host } catch (_: Exception) { null }
            host in setOf("auth.openai.com","chatgpt.com")
        }
}
