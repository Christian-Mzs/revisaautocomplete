package com.example

import android.content.Context
import android.text.InputType
import android.view.inputmethod.EditorInfo
import androidx.test.core.app.ApplicationProvider
import com.example.ime.KeyboardController
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class KeyboardSuggestionSafetyTest {
    private val context get() = ApplicationProvider.getApplicationContext<Context>()

    @Test fun `NO_SUGGESTIONS editor content is not read for word suggestions`() {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        val controller = KeyboardController(context, scope, onStateChanged = {})
        var reads = 0
        val ic = object : FakeInputConnection("private") {
            override fun getTextBeforeCursor(n: Int, flags: Int): CharSequence? {
                reads++
                return super.getTextBeforeCursor(n, flags)
            }
            override fun getSelectedText(flags: Int): CharSequence? {
                reads++
                return super.getSelectedText(flags)
            }
        }
        controller.updateInputConnection(ic, EditorInfo().apply {
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS
        })
        controller.handleDirectCharacter("x")
        assertEquals(0, reads)
        assertFalse(controller.wordSuggestions.isNotEmpty())
        scope.cancel()
    }

    @Test fun `space never silently replaces the typed word`() {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        val controller = KeyboardController(context, scope, onStateChanged = {})
        val ic = FakeInputConnection("nao")
        controller.updateInputConnection(ic, EditorInfo().apply { inputType = InputType.TYPE_CLASS_TEXT })
        controller.handleSpace()
        assertEquals("nao ", ic.currentText)
        scope.cancel()
    }
}
