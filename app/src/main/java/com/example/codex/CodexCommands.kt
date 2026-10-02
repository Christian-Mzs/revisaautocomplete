package com.example.codex

object CodexCommands {
    const val MODEL = "gpt-6-luna"
    fun validLoginStatus(code: Int, diagnostics: String): Boolean =
        !diagnostics.contains("Error loading configuration", ignoreCase=true) &&
        !diagnostics.contains("Function not implemented", ignoreCase=true) &&
        (code == 0 || (code == 1 && diagnostics.lineSequence().any { it.trim() == "Not logged in" }))
    fun textExecution(): List<String> = listOf(
            "exec", "--ephemeral", "--skip-git-repo-check", "-m", MODEL,
            "-c", "model_reasoning_effort=\"low\"",
            "-c", "model_reasoning_summary=\"none\"",
            "-c", "model_verbosity=\"low\"", "--sandbox", "read-only",
            "--color", "never", "--output-last-message", "/work/result.txt", "-"
        )
}
