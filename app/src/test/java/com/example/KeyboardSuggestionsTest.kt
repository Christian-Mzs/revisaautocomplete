package com.example

import android.content.Context
import android.text.InputType
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import androidx.test.core.app.ApplicationProvider
import com.example.ime.KeyboardController
import com.example.ime.TextActionUiState
import com.example.ime.ToolbarMode
import com.example.translation.TranslationService
import com.example.ui.keyboard.KeyboardLayoutView
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class KeyboardSuggestionsTest {
    private val context: Context get() = ApplicationProvider.getApplicationContext()
    private fun editor() = EditorInfo().apply { inputType = InputType.TYPE_CLASS_TEXT }

    @Test fun `toolbar defaults toggles and typing restores suggestions without autocorrect or network`() {
        val provider = FakeTranslationProvider()
        val scope = TestScope()
        val controller = KeyboardController(context, scope, TranslationService(provider = provider), onStateChanged = {})
        val ic = FakeInputConnection()
        controller.updateInputConnection(ic, editor())
        assertEquals(ToolbarMode.SUGGESTIONS, controller.toolbarMode)
        controller.toggleToolbarMode()
        assertEquals(ToolbarMode.TOOLS, controller.toolbarMode)
        controller.toggleToolbarMode()
        assertEquals(ToolbarMode.SUGGESTIONS, controller.toolbarMode)
        controller.toggleToolbarMode()
        for (letter in listOf("n", "a", "o")) controller.handleCharacter(letter)
        scope.testScheduler.advanceUntilIdle()
        assertEquals(ToolbarMode.SUGGESTIONS, controller.toolbarMode)
        assertEquals("nao", ic.currentText)
        assertEquals("não", controller.suggestions.first())
        controller.handleSpace()
        assertEquals("nao ", ic.currentText)
        assertTrue(controller.suggestions.isEmpty())
        controller.handleBackspace()
        scope.testScheduler.advanceUntilIdle()
        assertEquals("não", controller.suggestions.first())
        assertTrue(controller.applySuggestion("não"))
        assertEquals("não", ic.currentText)
        assertEquals(0, provider.callCount)
    }

    @Test fun `cursor updates replace candidates and idle restores default mode`() {
        val scope = TestScope()
        val controller = KeyboardController(context, scope, onStateChanged = {})
        val ic = FakeInputConnection("nao voce")
        controller.updateInputConnection(ic, editor())
        assertEquals("você", controller.suggestions.first())
        ic.setSelectionRange(3, 3)
        controller.onCursorChanged()
        scope.testScheduler.advanceUntilIdle()
        assertEquals("não", controller.suggestions.first())
        controller.toggleToolbarMode()
        controller.cancelAction()
        assertEquals(ToolbarMode.SUGGESTIONS, controller.toolbarMode)
        controller.toggleToolbarMode()
        controller.updateInputConnection(FakeInputConnection("voce"), editor())
        assertEquals(ToolbarMode.SUGGESTIONS, controller.toolbarMode)
    }

    @Test fun `rapid typing commits every character before coalesced editor reads`() {
        val scope = TestScope()
        var reads = 0
        val ic = object : FakeInputConnection() {
            override fun getSelectedText(flags: Int): CharSequence? {
                reads++
                return super.getSelectedText(flags)
            }
            override fun getTextBeforeCursor(n: Int, flags: Int): CharSequence? {
                reads++
                return super.getTextBeforeCursor(n, flags)
            }
            override fun getTextAfterCursor(n: Int, flags: Int): CharSequence? {
                reads++
                return super.getTextAfterCursor(n, flags)
            }
        }
        val controller = KeyboardController(context, scope, onStateChanged = {},
            suggestionDispatcher = kotlinx.coroutines.test.StandardTestDispatcher(scope.testScheduler))
        controller.updateInputConnection(ic, editor())
        reads = 0
        val typed = "teste de digitacao"
        for (char in typed) {
            controller.handleDirectCharacter(char.toString())
            controller.onCursorChanged()
        }
        assertEquals(typed, ic.currentText)
        assertEquals(0, reads)
        scope.testScheduler.advanceUntilIdle()
        assertEquals(3, reads)
    }

    @Test fun `unchanged toolbar is retained during render callbacks`() {
        val controller = KeyboardController(context, TestScope(), onStateChanged = {})
        val view = KeyboardLayoutView(context, controller)
        val toolbar = (view.getChildAt(0) as FrameLayout).getChildAt(0)
        repeat(20) { view.render() }
        assertSame(toolbar, (view.getChildAt(0) as FrameLayout).getChildAt(0))
    }

    @Test fun `password fields block suggestion reads during typing toggling and selection changes`() {
        val provider = FakeTranslationProvider()
        val controller = KeyboardController(context, TestScope(), TranslationService(provider = provider), onStateChanged = {})
        val ic = object : FakeInputConnection("secret") {
            override fun getSelectedText(flags: Int): CharSequence? = error("Password selection read")
            override fun getTextBeforeCursor(n: Int, flags: Int): CharSequence? = error("Password read")
            override fun getTextAfterCursor(n: Int, flags: Int): CharSequence? = error("Password read")
        }
        for (type in listOf(
            InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD,
            InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_WEB_PASSWORD,
            InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD,
            InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_VARIATION_PASSWORD
        )) {
            controller.updateInputConnection(ic, EditorInfo().apply { inputType = type })
            controller.toggleToolbarMode()
            controller.toggleToolbarMode()
            controller.handleCharacter("a")
            controller.handleDirectCharacter("1")
            controller.onCursorChanged()
            controller.cancelAction()
            assertTrue(controller.suggestions.isEmpty())
            assertFalse(controller.applySuggestion("não"))
        }
        assertEquals(0, provider.callCount)
    }

    @Test fun `toolbar taps use existing actions and only suggestion tap replaces word`() {
        lateinit var view: KeyboardLayoutView
        var switched = false
        val provider = FakeTranslationProvider()
        val controller = KeyboardController(context, TestScope(), TranslationService(provider = provider),
            onStateChanged = { view.render() }, onSwitchImeRequested = { switched = true })
        view = KeyboardLayoutView(context, controller)
        val ic = FakeInputConnection("eu nao quero", 6)
        controller.updateInputConnection(ic, editor())
        fun toolbar() = (view.getChildAt(0) as FrameLayout).getChildAt(0) as LinearLayout
        fun text(label: String) = (0 until toolbar().childCount).map { toolbar().getChildAt(it) }
            .filterIsInstance<TextView>().first { it.text.toString() == label }
        text("não").performClick()
        assertEquals("eu não quero", ic.currentText)
        text("›").performClick()
        assertEquals(ToolbarMode.TOOLS, controller.toolbarMode)
        toolbar().getChildAt(toolbar().childCount - 1).performClick()
        assertTrue(switched)
        text("‹").performClick()
        assertEquals(ToolbarMode.SUGGESTIONS, controller.toolbarMode)
        text("›").performClick()
        controller.consentManager.setConsentAccepted(false)
        text(context.getString(com.example.R.string.correct_action)).performClick()
        assertTrue(controller.uiState is TextActionUiState.ConsentRequired)
        controller.cancelAction()
        assertEquals(ToolbarMode.SUGGESTIONS, controller.toolbarMode)
        controller.consentManager.setConsentAccepted(true)
        text("›").performClick()
        text(context.getString(com.example.R.string.translate_action)).performClick()
        assertTrue(controller.uiState is TextActionUiState.SelectingLanguage)
        controller.cancelAction()
        assertEquals(0, provider.callCount)
    }

    @Test fun `geometry has five rows centered home row and evenly divided toolbar at different widths`() {
        val controller = KeyboardController(context, TestScope(), onStateChanged = {})
        val view = KeyboardLayoutView(context, controller)
        val rows = view.getChildAt(1) as LinearLayout
        assertEquals(5, rows.childCount)
        assertEquals(10, (rows.getChildAt(0) as LinearLayout).childCount)
        val qwerty = rows.getChildAt(1) as LinearLayout
        val home = rows.getChildAt(2) as LinearLayout
        assertEquals(11, home.childCount) // Nine letters and two weighted insets.
        val rowNumberHeight = rows.getChildAt(0).layoutParams.height
        assertTrue(rowNumberHeight < qwerty.layoutParams.height)
        for (widthDp in listOf(320, 411, 600)) {
            val width = (widthDp * context.resources.displayMetrics.density).toInt()
            view.measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED))
            view.layout(0, 0, width, view.measuredHeight)
            assertTrue(home.getChildAt(1).left > qwerty.getChildAt(0).left)
            val toolbar = (view.getChildAt(0) as ViewGroup).getChildAt(0) as LinearLayout
            val cells = listOf(toolbar.getChildAt(2), toolbar.getChildAt(4), toolbar.getChildAt(6))
            assertTrue(cells.maxOf { it.width } - cells.minOf { it.width } <= 2)
            assertTrue(cells.all { it.width > toolbar.getChildAt(0).width })
        }
    }

    @Test fun `typing refreshes toolbar without rebuilding held key views`() {
        lateinit var view: KeyboardLayoutView
        val controller = KeyboardController(context, TestScope(), onStateChanged = { view.render() })
        view = KeyboardLayoutView(context, controller)
        controller.updateInputConnection(FakeInputConnection(), editor())
        val rows = view.getChildAt(1) as LinearLayout
        val before = rows.getChildAt(3)
        controller.handleCharacter("n")
        assertSame(before, rows.getChildAt(3))
    }
}
