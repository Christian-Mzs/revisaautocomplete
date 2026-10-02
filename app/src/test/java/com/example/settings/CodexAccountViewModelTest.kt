package com.example.settings

import android.app.Application
import androidx.lifecycle.ViewModelStore
import androidx.test.core.app.ApplicationProvider
import com.example.codex.LoginState
import kotlinx.coroutines.*
import kotlinx.coroutines.test.*
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class CodexAccountViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private lateinit var backend: FakeAccount
    private lateinit var vm: CodexAccountViewModel
    private val store = ViewModelStore()
    @Before fun setUp() {
        Dispatchers.setMain(dispatcher)
        backend = FakeAccount()
        vm = CodexAccountViewModel(ApplicationProvider.getApplicationContext<Application>(), backend)
        store.put("account", vm)
    }
    @After fun tearDown() { store.clear(); dispatcher.scheduler.runCurrent(); Dispatchers.resetMain() }

    @Test fun `preparation precedes normal login and browser opening is explicit`() = runTest(dispatcher) {
        vm.login(); runCurrent()
        assertEquals(listOf("prepare", "login"), backend.calls)
        assertEquals(AccountOperation.LOGIN, vm.state.operation)
        assertTrue(vm.state.canOpenBrowser)
        assertEquals("https://auth.openai.com/oauth/authorize?secret=test", vm.browserIntent()!!.data.toString())
        assertEquals("android.intent.action.VIEW", vm.browserIntent()!!.action)
        backend.finish.complete(Unit); runCurrent()
        assertTrue(vm.state.connected)
        assertFalse(vm.state.busy)
        assertNull(vm.browserIntent())
    }

    @Test fun `cancel propagates and late output cannot restore URL or clear prior authentication`() = runTest(dispatcher) {
        backend.connected = true; vm.refresh(); runCurrent()
        vm.login(); runCurrent()
        vm.cancel(); runCurrent()
        assertTrue(backend.cancelled)
        assertTrue(vm.state.connected)
        assertFalse(vm.state.busy)
        assertNull(vm.browserIntent())
        backend.callback?.invoke("https://auth.openai.com/late?secret=ignored"); runCurrent()
        assertFalse(vm.state.canOpenBrowser)
        assertNull(vm.browserIntent())
        assertFalse(backend.calls.contains("logout"))
    }

    @Test fun `immediate cancellation does not leave preparation stuck`() = runTest(dispatcher) {
        vm.login(); vm.cancel(); runCurrent()
        assertFalse(vm.state.busy)
        assertNull(vm.browserIntent())
        assertTrue(backend.calls.isEmpty())
        vm.login(); runCurrent()
        assertTrue(vm.state.canOpenBrowser)
        vm.cancel(); runCurrent()
    }

    @Test fun `cancel preparation never starts login`() = runTest(dispatcher) {
        backend.holdPreparation = true
        vm.login(); runCurrent()
        assertEquals(AccountOperation.PREPARING, vm.state.operation)
        vm.cancel(); runCurrent()
        assertFalse(vm.state.busy)
        assertEquals(listOf("prepare"), backend.calls)
    }

    @Test fun `login failures are sanitized and busy prevents duplicate processes`() = runTest(dispatcher) {
        backend.fail = true
        vm.login(); vm.login(); runCurrent()
        assertEquals(1, backend.calls.count { it == "login" })
        assertEquals("Não foi possível concluir o login. Tente novamente.", vm.state.error)
        assertFalse(vm.state.toString().contains("secret"))
        assertFalse(vm.state.toString().contains("localhost"))
        assertNull(vm.browserIntent())
    }

    @Test fun `logout reflects verified runtime state`() = runTest(dispatcher) {
        backend.connected = true; vm.refresh(); runCurrent()
        assertTrue(vm.state.connected)
        vm.logout(); runCurrent()
        assertFalse(vm.state.connected)
        assertTrue(backend.calls.contains("logout"))
    }

    private class FakeAccount : AccountBackend {
        val calls = mutableListOf<String>()
        val finish = CompletableDeferred<Unit>()
        var connected = false
        var holdPreparation = false
        var fail = false
        var cancelled = false
        var callback: ((String) -> Unit)? = null
        override suspend fun prepare() {
            calls += "prepare"
            if (holdPreparation) awaitCancellation()
        }
        override suspend fun status(): LoginState { calls += "status"; return LoginState(connected) }
        override suspend fun login(onLine: (String) -> Unit) {
            calls += "login"; callback = onLine
            onLine("Starting local login server on http://localhost:1455...")
            onLine("https://auth.openai.com/oauth/authorize?secret=test")
            if (fail) error("raw diagnostics localhost secret")
            try { finish.await(); connected = true } catch (e: CancellationException) { cancelled = true; throw e }
        }
        override suspend fun logout(): LoginState { calls += "logout"; connected = false; return LoginState(false) }
    }
}
