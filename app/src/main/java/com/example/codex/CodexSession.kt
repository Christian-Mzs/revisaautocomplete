package com.example.codex

data class CliOutput(val code: Int, val stdout: String, val stderr: String)
data class LoginState(val connected: Boolean) {
    val label: String get() = if (connected) "Conectado" else "Não conectado"
}

/** All commands use the executor supplied by the same CodexRuntime instance. */
internal class CodexSession(private val execute: suspend (List<String>) -> CliOutput) {
    suspend fun status(): LoginState {
        val output = execute(listOf("login", "status"))
        val diagnostics = output.stdout + output.stderr
        check(CodexCommands.validLoginStatus(output.code, diagnostics)) {
            "Não foi possível verificar a sessão: ${diagnostics.takeLast(1800)}"
        }
        return LoginState(output.code == 0)
    }

    suspend fun logout(): LoginState {
        val output = execute(listOf("logout"))
        check(output.code == 0) {
            "Logout falhou (código ${output.code}): ${(output.stdout + output.stderr).takeLast(1800)}"
        }
        val state = status()
        check(!state.connected) { "O Codex ainda informa uma sessão conectada após o logout." }
        return state
    }
}
