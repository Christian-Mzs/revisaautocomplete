package com.example.settings

import android.app.Application
import android.content.Intent
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.codex.CodexRuntime
import com.example.codex.LoginLinks
import com.example.codex.LoginState
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/** UI boundary only: execution and process cancellation stay in the validated runtime. */
internal interface AccountBackend {
    suspend fun prepare()
    suspend fun status(): LoginState
    suspend fun login(onLine: (String) -> Unit)
    suspend fun logout(): LoginState
}

private class RuntimeAccountBackend(application: Application) : AccountBackend {
    private val runtime = CodexRuntime.getInstance(application)
    override suspend fun prepare() = runtime.ensureRuntimeReady()
    override suspend fun status() = runtime.loginStatus()
    override suspend fun login(onLine: (String) -> Unit) { runtime.login(false, onLine) }
    override suspend fun logout() = runtime.logout()
}

enum class AccountOperation { IDLE, PREPARING, CHECKING, LOGIN, LOGOUT }

data class AccountUiState(
    val connected: Boolean = false,
    val operation: AccountOperation = AccountOperation.IDLE,
    val canOpenBrowser: Boolean = false,
    val error: String? = null
) {
    val busy get() = operation != AccountOperation.IDLE
    val label get() = when {
        connected -> "✓ Conectado"
        operation == AccountOperation.LOGIN -> "Aguardando login"
        else -> "Não conectado"
    }
}

class CodexAccountViewModel internal constructor(
    application: Application,
    private val backend: AccountBackend
) : AndroidViewModel(application) {
    constructor(application: Application) : this(application, RuntimeAccountBackend(application))

    var state by mutableStateOf(AccountUiState())
        private set
    // The URL and CLI output never enter the UI's renderable state.
    private var loginUrl: String? = null
    private var task: Job? = null
    private var generation = 0

    private fun run(operation: AccountOperation, failure: String, block: suspend (Int) -> Unit) {
        if (state.busy) return
        val attempt = ++generation
        state = state.copy(operation = AccountOperation.PREPARING, error = null, canOpenBrowser = false)
        task = viewModelScope.launch {
            try {
                backend.prepare()
                state = state.copy(operation = operation)
                block(attempt)
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                state = state.copy(error = failure)
            } finally {
                generation++
                loginUrl = null
                state = state.copy(operation = AccountOperation.IDLE, canOpenBrowser = false)
                task = null
            }
        }
    }

    fun refresh() = run(AccountOperation.CHECKING, "Não foi possível verificar sua conta. Tente novamente.") {
        state = state.copy(connected = backend.status().connected)
    }

    fun login() = run(AccountOperation.LOGIN, "Não foi possível concluir o login. Tente novamente.") { attempt ->
        backend.login onLine@{ line ->
            // Reader callbacks can arrive from either pipe, including after cancellation.
            val url = LoginLinks.find(line) ?: return@onLine
            viewModelScope.launch {
                if (attempt == generation && state.operation == AccountOperation.LOGIN) {
                    loginUrl = url
                    state = state.copy(canOpenBrowser = true)
                }
            }
        }
        state = state.copy(connected = backend.status().connected)
    }

    fun logout() = run(AccountOperation.LOGOUT, "Não foi possível sair da conta. Tente novamente.") {
        state = state.copy(connected = backend.logout().connected)
    }

    fun cancel() {
        generation++
        loginUrl = null
        state = state.copy(canOpenBrowser = false, error = null)
        task?.cancel() // Propagates to CodexRuntime.execute's process cleanup.
    }

    fun browserIntent(): Intent? = loginUrl?.takeIf { state.canOpenBrowser }?.let {
        Intent(Intent.ACTION_VIEW, Uri.parse(it))
    }

    fun browserUnavailable() {
        state = state.copy(error = "Não foi possível abrir o navegador. Tente novamente.")
    }
}
