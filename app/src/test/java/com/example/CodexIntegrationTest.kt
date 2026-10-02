package com.example

import android.content.Context
import android.text.InputType
import android.view.inputmethod.EditorInfo
import androidx.test.core.app.ApplicationProvider
import com.example.codex.*
import com.example.ime.*
import kotlinx.coroutines.*
import kotlinx.coroutines.test.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

class FakeCodexEngine : CodexTextEngine {
    var connected = true
    var ensureCount = 0
    var cancelled = false
    var wait = false
    var failure = false
    val calls = mutableListOf<Pair<String,TextOperation>>()
    override suspend fun ensureRuntimeReady() { ensureCount++ }
    override suspend fun loginStatus() = LoginState(connected)
    override suspend fun processText(text: String, operation: TextOperation): String {
        calls.add(text to operation)
        if (failure) error("Codex indisponível")
        if (wait) try { awaitCancellation() } finally { cancelled = true }
        return "resultado final"
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk=[34])
class CodexIntegrationTest {
    private fun controller(scope: CoroutineScope, engine: FakeCodexEngine, ic: FakeInputConnection): KeyboardController {
        val context = ApplicationProvider.getApplicationContext<Context>()
        return KeyboardController(context, scope, textEngine=engine, onStateChanged={}).also {
            it.consentManager.setConsentAccepted(true)
            it.updateInputConnection(ic, EditorInfo().apply { inputType = InputType.TYPE_CLASS_TEXT })
        }
    }
    @Test fun `no login means no field read and no text execution`() = runTest {
        val engine = FakeCodexEngine().apply { connected=false }
        var read = false
        val ic = object : FakeInputConnection("texto") {
            override fun getSelectedText(flags: Int): CharSequence? { read=true; return super.getSelectedText(flags) }
            override fun getTextBeforeCursor(n: Int, flags: Int): CharSequence? { read=true; return super.getTextBeforeCursor(n,flags) }
            override fun getTextAfterCursor(n: Int, flags: Int): CharSequence? { read=true; return super.getTextAfterCursor(n,flags) }
        }
        val c = controller(this,engine,ic)
        c.requestCorrection(); runCurrent()
        assertTrue(c.uiState is TextActionUiState.LoginRequired)
        assertTrue(engine.calls.isEmpty()); assertFalse(read)
        c.cancelAction(); c.requestTranslationPicker(); runCurrent()
        assertTrue(c.uiState is TextActionUiState.LoginRequired)
        assertTrue(engine.calls.isEmpty()); assertFalse(read)
        c.cancelAction(); c.selectLanguageAndTranslate("en"); runCurrent()
        assertTrue(c.uiState is TextActionUiState.LoginRequired)
        assertTrue(engine.calls.isEmpty()); assertFalse(read)
    }
    @Test fun `correction and translation keep preview and replacement flow`() = runTest {
        val engine = FakeCodexEngine()
        val ic = FakeInputConnection("texto original")
        val c = controller(this,engine,ic)
        c.requestCorrection(); runCurrent()
        assertTrue(c.uiState is TextActionUiState.Preview)
        assertEquals(TextMode.CORRECTION,engine.calls.single().second.mode)
        assertEquals("texto original",engine.calls.single().first)
        assertEquals("resultado final",(c.uiState as TextActionUiState.Preview).resultText)
        c.togglePreviewOriginal(); assertTrue((c.uiState as TextActionUiState.Preview).isShowingOriginal)
        c.cancelAction(); c.requestTranslationPicker(); runCurrent()
        assertTrue(c.uiState is TextActionUiState.SelectingLanguage)
        c.selectLanguageAndTranslate("en"); runCurrent()
        assertEquals(TextMode.TRANSLATION,engine.calls.last().second.mode)
        assertEquals("English",engine.calls.last().second.targetLanguage.promptName)
        c.applyResult(); assertTrue(c.uiState is TextActionUiState.Idle)
    }
    @Test fun `cancel propagates to executor and suppresses preview`() = runTest {
        val engine = FakeCodexEngine().apply { wait=true }
        val c = controller(this,engine,FakeInputConnection("texto"))
        c.requestCorrection(); runCurrent()
        assertTrue(c.uiState is TextActionUiState.Processing)
        c.requestCorrection(); assertEquals(1,engine.calls.size)
        c.cancelAction(); runCurrent()
        assertTrue(engine.cancelled); assertTrue(c.uiState is TextActionUiState.Idle)
    }
    @Test fun `editor switch cancels operation before returning output to another field`() = runTest {
        val engine = FakeCodexEngine().apply { wait=true }
        val c = controller(this,engine,FakeInputConnection("texto"))
        c.requestCorrection(); runCurrent()
        c.updateInputConnection(FakeInputConnection("senha"),EditorInfo().apply {
            inputType=InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
        })
        runCurrent(); assertTrue(engine.cancelled); assertTrue(c.uiState is TextActionUiState.Idle)
    }
    @Test fun `Codex error is shown without a fallback`() = runTest {
        val engine = FakeCodexEngine().apply { failure=true }
        val c = controller(this,engine,FakeInputConnection("texto"))
        c.requestCorrection(); runCurrent()
        assertEquals("Codex indisponível",(c.uiState as TextActionUiState.Error).message)
        assertEquals(1,engine.calls.size)
    }
}
