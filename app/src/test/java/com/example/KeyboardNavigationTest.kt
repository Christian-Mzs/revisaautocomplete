package com.example

import android.content.Context
import android.os.SystemClock
import android.view.MotionEvent
import android.view.View
import android.view.inputmethod.EditorInfo
import android.text.InputType
import android.widget.TextView
import android.view.ViewGroup
import androidx.test.core.app.ApplicationProvider
import com.example.ime.KeyboardController
import com.example.ime.KeyboardMode
import com.example.ui.keyboard.KeyboardLayoutView
import com.example.ui.keyboard.EmojiSwipeGrid
import kotlinx.coroutines.test.TestScope
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class KeyboardNavigationTest {
    private val context: Context get() = ApplicationProvider.getApplicationContext()
    private fun descendants(group: ViewGroup): List<View> =
        (0 until group.childCount).flatMap {
            val child = group.getChildAt(it)
            listOf(child) + if (child is ViewGroup) descendants(child) else emptyList()
        }

    @Test fun emojiFooterHasOnlyLettersReturnAndClipboardPastesWholeItem() {
        val ic = FakeInputConnection()
        lateinit var view: KeyboardLayoutView
        val controller = KeyboardController(context, TestScope(), onStateChanged = { view.render() })
        view = KeyboardLayoutView(context, controller)
        controller.updateInputConnection(ic, EditorInfo().apply { inputType = InputType.TYPE_CLASS_TEXT })
        controller.setMode(KeyboardMode.EMOJIS)
        val labels = descendants(view).filterIsInstance<TextView>().map { it.text.toString() }
        assertTrue(labels.contains("ABC"))
        assertFalse(labels.contains("?123"))
        controller.clipboardHistory.add("texto copiado")
        descendants(view).first { it.contentDescription == "Área de transferência" }.performClick()
        descendants(view).filterIsInstance<TextView>().first { it.text.toString() == "texto copiado" }.performClick()
        assertEquals("texto copiado", ic.currentText)
        controller.setMode(KeyboardMode.LETTERS)
        view.resetNavigation()
        assertFalse(descendants(view).filterIsInstance<TextView>().any { it.text.toString() == "texto copiado" })
        assertEquals(KeyboardMode.LETTERS, controller.currentMode)
    }

    @Test fun diagnosticOverlayPreservesToolbarToolsAndCanBeDismissed() {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
        clipboard.clearPrimaryClip()
        lateinit var view: KeyboardLayoutView
        val controller = KeyboardController(context, TestScope(), onStateChanged = { view.render() })
        view = KeyboardLayoutView(context, controller)
        controller.updateInputConnection(FakeInputConnection(), EditorInfo().apply {
            inputType = InputType.TYPE_CLASS_TEXT
        })
        val container = view.getChildAt(0) as android.widget.FrameLayout
        val toolbar = container.getChildAt(0)
        assertTrue(toolbar is android.widget.LinearLayout)
        for (label in listOf(context.getString(R.string.open_emojis),
            context.getString(R.string.correct_action), context.getString(R.string.translate_action),
            "Área de transferência")) {
            assertTrue(descendants(view).any { it.contentDescription == label })
        }
        val overlay = descendants(view).first { it.tag == "clipboard_diagnostic_overlay" }
        assertTrue((overlay as TextView).text.toString().contains("NO_PRIMARY_CLIP"))
        overlay.performClick()
        assertEquals(View.GONE, overlay.visibility)
        assertSame(toolbar, container.getChildAt(0))
        descendants(view).first { it.contentDescription == context.getString(R.string.open_emojis) }.performClick()
        assertEquals(KeyboardMode.EMOJIS, controller.currentMode)
        view.dismissPopup()
    }

    @Test fun horizontalSwipesChangeCategoryAndVerticalScrollDoesNot() {
        val changes = mutableListOf<Int>()
        val parent = android.widget.FrameLayout(context)
        val grid = EmojiSwipeGrid(context) { changes.add(it) }
        parent.addView(grid)
        fun event(action: Int, x: Float, y: Float): MotionEvent =
            MotionEvent.obtain(SystemClock.uptimeMillis(), SystemClock.uptimeMillis(), action, x, y, 0)
        fun gesture(dx: Float, dy: Float, expectedIntercept: Boolean) {
            event(MotionEvent.ACTION_DOWN, 200f, 200f).let { e ->
                grid.onInterceptTouchEvent(e); e.recycle()
            }
            event(MotionEvent.ACTION_MOVE, 200f + dx, 200f + dy).let { e ->
                val intercepted = grid.onInterceptTouchEvent(e)
                if (expectedIntercept) assertTrue(intercepted)
                e.recycle()
            }
            event(MotionEvent.ACTION_UP, 200f + dx, 200f + dy).let { e ->
                grid.onTouchEvent(e); e.recycle()
            }
        }
        gesture(-150f, 5f, true)
        gesture(150f, 5f, true)
        gesture(5f, 150f, false)
        assertEquals(listOf(1, -1), changes)
    }
}
