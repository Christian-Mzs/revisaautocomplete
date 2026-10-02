package com.example

import android.content.Context
import android.text.InputType
import android.view.inputmethod.EditorInfo
import androidx.test.core.app.ApplicationProvider
import com.example.ime.KeyboardController
import com.example.ime.TextActionUiState
import com.example.codex.*
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class SensitiveFieldSecurityTest {

    private class SpyingInputConnection(initialText: String) : FakeInputConnection(initialText) {
        var wasTextRead: Boolean = false

        override fun getTextBeforeCursor(n: Int, flags: Int): CharSequence? {
            wasTextRead = true
            return super.getTextBeforeCursor(n, flags)
        }

        override fun getTextAfterCursor(n: Int, flags: Int): CharSequence? {
            wasTextRead = true
            return super.getTextAfterCursor(n, flags)
        }

        override fun getSelectedText(flags: Int): CharSequence? {
            wasTextRead = true
            return super.getSelectedText(flags)
        }
    }

    @Test
    fun `sensitive password field blocks correction BEFORE reading text or making network calls`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val testDispatcher = StandardTestDispatcher()
        val testScope = TestScope(testDispatcher)

        val engine = FakeCodexEngine()

        val controller = KeyboardController(
            context = context,
            coroutineScope = testScope,
            textEngine = engine,
            onStateChanged = {}
        )
        // Mark consent as accepted so we test the security gate specifically
        controller.consentManager.setConsentAccepted(true)

        val spyingIc = SpyingInputConnection("secret_password_123")
        val passwordEditorInfo = EditorInfo().apply {
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
        }

        controller.updateInputConnection(spyingIc, passwordEditorInfo)

        assertTrue(controller.isSensitiveField)

        // Attempt correction
        controller.requestCorrection()

        // 1. UI State must show error/blocked
        assertTrue(controller.uiState is TextActionUiState.Error)
        val errorMsg = (controller.uiState as TextActionUiState.Error).message
        assertTrue(errorMsg.contains("senha", ignoreCase = true))

        // 2. InputConnection text MUST NOT have been read
        assertFalse("Text must never be read from sensitive fields", spyingIc.wasTextRead)

        // 3. Provider MUST NOT have been called
        assertEquals(0, engine.calls.size + engine.ensureCount)
    }

    @Test
    fun `sensitive password field blocks translation BEFORE reading text or making network calls`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val testDispatcher = StandardTestDispatcher()
        val testScope = TestScope(testDispatcher)

        val engine = FakeCodexEngine()

        val controller = KeyboardController(
            context = context,
            coroutineScope = testScope,
            textEngine = engine,
            onStateChanged = {}
        )
        controller.consentManager.setConsentAccepted(true)

        val spyingIc = SpyingInputConnection("credit_card_pin_9876")
        val pinEditorInfo = EditorInfo().apply {
            inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_VARIATION_PASSWORD
        }

        controller.updateInputConnection(spyingIc, pinEditorInfo)

        assertTrue(controller.isSensitiveField)

        // Attempt translation
        controller.requestTranslationPicker()

        // 1. UI State must show error/blocked
        assertTrue(controller.uiState is TextActionUiState.Error)

        // 2. Directly attempting to translate a language must also be blocked
        controller.selectLanguageAndTranslate("en")

        // 3. InputConnection text MUST NOT have been read
        assertFalse("Text must never be read from sensitive fields", spyingIc.wasTextRead)

        // 4. Provider MUST NOT have been called
        assertEquals(0, engine.calls.size + engine.ensureCount)
    }
}
