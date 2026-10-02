package com.example.settings

import android.app.Application
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.codex.CodexRuntime
import com.example.codex.LoginLinks
import kotlinx.coroutines.*

class CodexAccountViewModel(application: Application) : AndroidViewModel(application) {
    private val runtime = CodexRuntime.getInstance(application)
    var status by mutableStateOf("Verificando conta…")
        private set
    var busy by mutableStateOf(false)
        private set
    var error by mutableStateOf("")
        private set
    var deviceAuth by mutableStateOf(false)
    var instructions by mutableStateOf("")
        private set
    var loginUrl by mutableStateOf<String?>(null)
        private set
    private var task: Job? = null
    private var generation = 0

    init { refresh() }
    private fun run(block: suspend () -> Unit) {
        if (busy) return
        busy = true; error = ""
        task = viewModelScope.launch {
            try { runtime.ensureRuntimeReady(); block() }
            catch (e: CancellationException) { status = "Operação interrompida"; throw e }
            catch (e: Exception) { status = "Conta não confirmada"; error = e.message ?: "Falha ao acessar ChatGPT." }
            finally { busy = false; task = null }
        }
    }
    fun refresh() = run {
        status = runtime.loginStatus().label
    }
    fun login() = run {
        val attempt = ++generation
        status = "Conclua o login no navegador"
        try {
            runtime.login(deviceAuth) { line ->
                viewModelScope.launch {
                    if (attempt != generation) return@launch
                    instructions = (instructions + line + "\n").takeLast(8000)
                    LoginLinks.find(line)?.let { loginUrl = it }
                }
            }
            status = runtime.loginStatus().label
        } finally { generation++; instructions = ""; loginUrl = null }
    }
    fun logout() = run { status = runtime.logout().label }
    fun cancel() { generation++; task?.cancel(); instructions = ""; loginUrl = null }
}

@Composable
fun CodexAccountCard(vm: CodexAccountViewModel = viewModel()) {
    val context = LocalContext.current
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement=Arrangement.spacedBy(8.dp)) {
            Text("ChatGPT", style=MaterialTheme.typography.titleMedium)
            Text(vm.status)
            Text("Correção e tradução usam Codex/OpenAI com sua conta ChatGPT.")
            Row {
                Button(onClick=vm::login,enabled=!vm.busy) { Text("Entrar com ChatGPT") }
            }
            OutlinedButton(onClick=vm::logout,enabled=!vm.busy) { Text("Sair da conta") }
            Row {
                Checkbox(checked=vm.deviceAuth,onCheckedChange={vm.deviceAuth=it},enabled=!vm.busy)
                Text("Usar código de dispositivo (alternativa)")
            }
            vm.loginUrl?.let { url ->
                Button(onClick={
                    runCatching { context.startActivity(Intent(Intent.ACTION_VIEW,Uri.parse(url))) }
                }) { Text("Abrir login no navegador") }
                SelectionContainer { Text(url) }
            }
            if (vm.instructions.isNotBlank()) SelectionContainer { Text(vm.instructions) }
            if (vm.error.isNotBlank()) Text(vm.error, color=MaterialTheme.colorScheme.error)
            if (vm.busy) {
                LinearProgressIndicator(Modifier.fillMaxWidth())
                OutlinedButton(onClick=vm::cancel) { Text("Cancelar") }
            }
        }
    }
}
