package com.example.codex

import android.content.Context
import android.os.Build
import android.system.Os
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.json.JSONArray
import java.io.File
import java.io.InputStream
import java.util.zip.ZipInputStream
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.concurrent.thread

/** PRoot is a compatibility layer, not a security sandbox. The Android app UID is the boundary. */
class CodexRuntime private constructor(context: Context) : CodexTextEngine {
    companion object {
        @Volatile private var instance: CodexRuntime? = null
        fun getInstance(context: Context): CodexRuntime = instance ?: synchronized(this) {
            instance ?: CodexRuntime(context.applicationContext).also { instance = it }
        }
    }
    private val app = context.applicationContext
    private val base = File(app.filesDir, "codex-runtime")
    private val root = File(base, "rootfs")
    private val native = File(app.applicationInfo.nativeLibraryDir)
    val codexHome = File(base, "codex-home")
    private val home = File(base, "home")
    private val work = File(base, "work")
    private val tmp = File(base, "tmp")
    private val lock = Mutex()
    private var verified = false
    private val session = CodexSession { args -> execute(codexArgs(args),timeoutMs=30_000) }

    fun installed(): Boolean = File(root,".ready").isFile &&
        listOf("libproot.so", "libproot-loader.so", "libcodex.so", "libruntime-probe.so").all { File(native,it).isFile }

    private fun confined(parent: File, path: String): File {
        require(!path.startsWith("/") && path.split('/').none { it == ".." }) { "Caminho inválido no runtime." }
        val file = File(parent,path)
        require(file.canonicalPath.startsWith(parent.canonicalPath + "/")) { "Caminho fora do runtime." }
        return file
    }

    override suspend fun ensureRuntimeReady() = lock.withLock {
        val expected = withContext(Dispatchers.IO) {
            val version = app.assets.open("runtime/version.txt").bufferedReader().use { it.readText().trim() }
            val provenance = app.assets.open("runtime/provenance.json").use { it.readBytes() }
            val digest = java.security.MessageDigest.getInstance("SHA-256").digest(provenance)
                .joinToString("") { "%02x".format(it) }
            "${version}_${digest}_revisa1"
        }
        val validation = File(root,".validated")
        val cached = withContext(Dispatchers.IO) {
            RuntimeReadiness.matches(expected, validation.takeIf { it.isFile }?.readText(),
                installed() && File(codexHome,"config.toml").isFile)
        }
        if (cached) { verified = true; return@withLock }
        prepareUnlocked()
        withContext(Dispatchers.IO) {
            val pending = File(root,".validated-pending")
            pending.writeText(expected)
            check(pending.renameTo(validation)) { "Não foi possível salvar a preparação." }
        }
    }

    private suspend fun prepareUnlocked(onStage: suspend (String) -> Unit = {}): String {
        verified = false
        onStage("Instalando arquivos privados…")
        withContext(Dispatchers.IO) {
            require("arm64-v8a" in Build.SUPPORTED_ABIS) { "Esta versão requer Android ARM64." }
            require(listOf("libproot.so", "libproot-loader.so", "libcodex.so", "libruntime-probe.so").all { File(native,it).isFile }) {
                "APK sem os binários. Execute tools/prepare_runtime.py antes de compilar."
            }
            listOf(base,codexHome,home,work,tmp).forEach { it.mkdirs(); Os.chmod(it.path,448) }
            val version = app.assets.open("runtime/version.txt").bufferedReader().use { it.readText().trim() }
            if (File(root,".ready").takeIf { it.isFile }?.readText() != version) {
                val stage = File(base,"rootfs-stage")
                stage.deleteRecursively(); stage.mkdirs()
                try {
                    ZipInputStream(app.assets.open("runtime/rootfs.zip")).use { zip ->
                        while (true) {
                            val entry = zip.nextEntry ?: break
                            val dest = confined(stage,entry.name)
                            dest.parentFile!!.mkdirs()
                            if (entry.isDirectory) dest.mkdirs() else dest.outputStream().use { zip.copyTo(it) }
                            zip.closeEntry()
                        }
                    }
                    val manifest = JSONArray(app.assets.open("runtime/manifest.json").bufferedReader().use { it.readText() })
                    // Create real directories and set file modes before creating any links.
                    for (i in 0 until manifest.length()) {
                        val item = manifest.getJSONObject(i)
                        if (item.getString("kind") == "symlink") continue
                        val dest = confined(stage,item.getString("path"))
                        if (item.getString("kind") == "directory") dest.mkdirs()
                        Os.chmod(dest.path,item.getInt("mode"))
                    }
                    for (i in 0 until manifest.length()) {
                        val item = manifest.getJSONObject(i)
                        if (item.getString("kind") != "symlink") continue
                        val dest = confined(stage,item.getString("path"))
                        val target = item.getString("target")
                        require(!target.startsWith("/"))
                        require(File(dest.parentFile,target).canonicalPath.startsWith(stage.canonicalPath + "/"))
                        dest.parentFile!!.mkdirs()
                        Os.symlink(target,dest.path)
                    }
                    listOf("proc","dev/shm","work","home/revisa","codex-home","tmp","usr/local/bin").forEach { File(stage,it).mkdirs() }
                    File(stage,"usr/local/bin/codex").writeText("")
                    File(stage,"usr/local/bin/runtime-probe").writeText("")
                    File(stage,"etc/resolv.conf").apply { delete(); writeText("nameserver 1.1.1.1\nnameserver 8.8.8.8\n") }
                    File(stage,"etc/hosts").writeText("127.0.0.1 localhost\n::1 localhost\n")
                    root.deleteRecursively()
                    check(stage.renameTo(root)) { "Não foi possível instalar o rootfs." }
                    File(root,".ready").writeText(version)
                } catch (e: Exception) { stage.deleteRecursively(); throw e }
            }
            // File storage is needed because Alpine has no OS keyring. Never log/read auth.json in the UI.
            File(codexHome,"config.toml").writeText("""
                cli_auth_credentials_store = "file"
                model = "gpt-6-luna"
                model_reasoning_effort = "low"
                approval_policy = "never"
                sandbox_mode = "read-only"
                [features]
                shell_tool = false
            """.trimIndent()+"\n")
        }
        onStage("Verificando Alpine / PRoot…")
        val linux = execute(listOf("/bin/busybox","uname","-m"),timeoutMs=15_000)
        check(linux.code == 0) { "Alpine não iniciou: ${linux.stderr.take(1500)}" }
        // Test actual file operations, not just a CLI flag that skips config initialization.
        val files = execute(listOf("/bin/sh", "-c", """
            set -eu
            probe=/codex-home/runtime-probe
            trap 'rm -rf "${'$'}probe"' EXIT
            echo 'Etapa: diretório de trabalho'
            pwd
            echo 'Etapa: criar diretório'
            mkdir -p "${'$'}probe"
            echo 'Etapa: permissões'
            chmod 700 "${'$'}probe"
            echo 'Etapa: gravar e ler arquivo'
            printf 'revisa-probe' > "${'$'}probe/text"
            test "${'$'}(cat "${'$'}probe/text")" = revisa-probe
            echo 'Etapa: link simbólico'
            ln -sf text "${'$'}probe/link"
            test "${'$'}(cat "${'$'}probe/link")" = revisa-probe
            echo 'Etapa: renomear arquivo'
            mv "${'$'}probe/text" "${'$'}probe/renamed"
            echo 'Etapa: concluída'
        """.trimIndent()),timeoutMs=30_000)
        check(files.code == 0) {
            "Teste de arquivos do runtime falhou: ${files.stdout.takeLast(1200)} ${files.stderr.takeLast(1200)}"
        }
        onStage("Verificando chamadas de sistema (statx, arquivos e locks)…")
        val nativeProbe = execute(listOf("/usr/local/bin/runtime-probe"),timeoutMs=30_000)
        check(nativeProbe.code == 0) {
            "Compatibilidade Linux falhou antes do Codex: ${nativeProbe.stdout.takeLast(1200)} ${nativeProbe.stderr.takeLast(1800)}"
        }
        onStage("Verificando executável do Codex…")
        val help = execute(listOf("/usr/local/bin/codex","--help"),timeoutMs=30_000)
        check(help.code == 0) { "Codex não iniciou: ${help.stderr.take(1500)}" }
        val version = execute(codexArgs(listOf("--version")),timeoutMs=30_000)
        check(version.code == 0) { "Codex falhou: ${version.stderr.take(1500)}" }
        onStage("Verificando configuração do Codex (sem iniciar login)…")
        val configuration = execute(codexArgs(listOf("login","status")),timeoutMs=30_000)
        val diagnostics = configuration.stdout + configuration.stderr
        if (!CodexCommands.validLoginStatus(configuration.code, diagnostics)) {
            val syscalls = mutableListOf<String>()
            if (diagnostics.contains("Function not implemented",true)) {
                try {
                    execute(codexArgs(listOf("login","status")),timeoutMs=30_000,trace=true,onLine={ line ->
                        if (Regex("= 0x(?:ffffffffffffffda|ffffffda)\\b",RegexOption.IGNORE_CASE).containsMatchIn(line)) {
                            synchronized(syscalls) {
                                syscalls.add(line.take(350))
                                if (syscalls.size > 6) syscalls.removeAt(0)
                            }
                        }
                    })
                } catch (e: TimeoutCancellationException) { /* Keep the original configuration failure. */ }
            }
            val trace = synchronized(syscalls) { syscalls.joinToString("\n") }
            error("Configuração do Codex falhou antes do login: ${diagnostics.takeLast(1800)}" +
                if (trace.isNotBlank()) "\nChamadas que retornaram ENOSYS:\n$trace" else "")
        }
        verified = true
        return "PRoot Termux / Alpine ARM64 · chamadas de sistema e configuração verificadas · ${version.stdout.trim()}"
    }

    private fun codexArgs(args: List<String>): List<String> = listOf("/usr/local/bin/codex") + args

    override suspend fun loginStatus(): LoginState {
        ensureRuntimeReady()
        return lock.withLock {
            check(installed()) { "Runtime não preparado" }
            session.status()
        }
    }

    suspend fun logout(): LoginState {
        ensureRuntimeReady()
        return lock.withLock {
            check(verified) { "Prepare e verifique o runtime antes de sair." }
            session.logout()
        }
    }

    suspend fun login(deviceAuth: Boolean, onLine: (String) -> Unit): String {
        ensureRuntimeReady()
        return lock.withLock {
            check(verified) { "Prepare e verifique o runtime antes de entrar." }
            val args = listOf("login") + if (deviceAuth) listOf("--device-auth") else emptyList()
            val result = execute(codexArgs(args), timeoutMs=300_000,onLine=onLine)
            check(result.code == 0) { "Login não concluído: ${(result.stderr+result.stdout).takeLast(1800)}" }
            "Login concluído. As credenciais foram persistidas."
        }
    }

    override suspend fun processText(text: String, operation: TextOperation): String {
        ensureRuntimeReady()
        return lock.withLock {
            require(text.isNotBlank()) { "Digite uma frase." }
            check(verified) { "Prepare e verifique o runtime antes de executar." }
            if (!session.status().connected) throw CodexLoginRequiredException()
            withContext(Dispatchers.IO) { work.deleteRecursively(); work.mkdirs(); Os.chmod(work.path,448) }
            try {
                val result = execute(codexArgs(operation.command()),
                    stdin=operation.prompt(text),timeoutMs=180_000)
                check(result.code == 0) { "Codex saiu com código ${result.code}: ${result.stderr.takeLast(2000)}" }
                // Both operations display only the final-message file, never diagnostics.
                withContext(Dispatchers.IO) {
                    FinalMessageReader.read(work)
                }
            } finally { withContext(NonCancellable + Dispatchers.IO) { work.deleteRecursively(); work.mkdirs() } }
        }
    }

    private suspend fun execute(args: List<String>, stdin: String = "", timeoutMs: Long, trace: Boolean = false,
                                onLine: ((String) -> Unit)? = null): CliOutput = withContext(Dispatchers.IO) {
        listOf(work,home,tmp,codexHome).forEach { it.mkdirs() }
        val command = listOf(File(native,"libproot.so").path,"--kill-on-exit") +
            (if (trace) listOf("-v","9") else emptyList()) + listOf("-0","--link2symlink","-L","--sysvipc","-r",root.path,
            "-b","/dev/null:/dev/null","-b","/dev/urandom:/dev/urandom","-b","/dev/random:/dev/random",
            "-b","/proc:/proc","-b","${work.path}:/work","-b","${home.path}:/home/revisa",
            "-b","${codexHome.path}:/codex-home","-b","${tmp.path}:/tmp",
            "-b","${tmp.path}:/dev/shm",
            "-b","${File(native,"libcodex.so").path}:/usr/local/bin/codex","-w","/work",
            "-b","${File(native,"libruntime-probe.so").path}:/usr/local/bin/runtime-probe",
            "/usr/bin/env","-i","HOME=/home/revisa","CODEX_HOME=/codex-home",
            "PATH=/usr/local/bin:/usr/bin:/bin","TMPDIR=/tmp","LANG=C.UTF-8","TERM=dumb",
            "BROWSER=/bin/true","SSL_CERT_FILE=/etc/ssl/cert.pem") + args
        val builder = ProcessBuilder(command).directory(base)
        builder.environment().clear()
        builder.environment().putAll(mapOf("PROOT_LOADER" to File(native,"libproot-loader.so").path,
            "PROOT_TMP_DIR" to tmp.path,"PROOT_NO_SECCOMP" to "1","PATH" to "/system/bin"))
        withTimeout(timeoutMs) {
            suspendCancellableCoroutine { continuation ->
                val process = builder.start()
                fun stop() {
                    process.destroy()
                    if (process.isAlive) process.destroyForcibly()
                    runCatching { process.inputStream.close() }
                    runCatching { process.errorStream.close() }
                    runCatching { process.outputStream.close() }
                }
                continuation.invokeOnCancellation { stop() }
                // Dedicated daemon threads keep pipe reads from delaying coroutine cancellation.
                thread(name="revisa-codex-process",isDaemon=true) {
                    try {
                        var stdout = ""
                        var stderr = ""
                        val out = thread(isDaemon=true) { runCatching { stdout = capture(process.inputStream,onLine) } }
                        val err = thread(isDaemon=true) { runCatching { stderr = capture(process.errorStream,onLine) } }
                        process.outputStream.bufferedWriter().use { it.write(stdin) }
                        val code = process.waitFor()
                        out.join(); err.join()
                        if (continuation.isActive) continuation.resume(CliOutput(code,stdout,stderr))
                    } catch (e: Exception) {
                        if (continuation.isActive) continuation.resumeWithException(e)
                    } finally { stop() }
                }
            }
        }
    }

    private fun capture(input: InputStream,onLine: ((String) -> Unit)?): String {
        val result = StringBuilder()
        input.bufferedReader().use { reader ->
            reader.forEachLine { raw ->
                val line = raw.replace(Regex("\u001B\\[[0-?]*[ -/]*[@-~]"),"")
                onLine?.invoke(line.take(8000))
                result.append(line.take(8000)).append('\n')
                if (result.length > 32_000) result.delete(0,result.length-32_000)
            }
        }
        return result.toString()
    }
}
