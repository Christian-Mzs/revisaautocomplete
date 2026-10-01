package com.example

import android.content.Context
import android.os.Looper
import android.os.SystemClock
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.ViewConfiguration
import android.view.inputmethod.EditorInfo
import android.text.InputType
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import androidx.test.core.app.ApplicationProvider
import com.example.ime.KeyboardController
import com.example.ime.ShiftState
import com.example.ui.keyboard.KeyboardLayoutView
import kotlinx.coroutines.test.TestScope
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.time.Duration

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class KeyboardTouchTest {
    private val context: Context get() = ApplicationProvider.getApplicationContext()

    private fun keys(group: ViewGroup): List<View> = (0 until group.childCount).flatMap { index ->
        val child = group.getChildAt(index)
        if (child.tag is String) listOf(child)
        else if (child is ViewGroup) keys(child) else emptyList()
    }

    private fun touch(key: View, action: Int, x: Float = key.width / 2f) {
        val time = SystemClock.uptimeMillis()
        val event = MotionEvent.obtain(time, time, action, x, key.height / 2f, 0)
        try { assertTrue(key.dispatchTouchEvent(event)) } finally { event.recycle() }
    }

    private fun keyboard(ic: FakeInputConnection): Pair<KeyboardController, KeyboardLayoutView> {
        lateinit var view: KeyboardLayoutView
        val controller = KeyboardController(context, TestScope(), onStateChanged = { view.render() })
        view = KeyboardLayoutView(context, controller)
        controller.updateInputConnection(ic, EditorInfo().apply { inputType = InputType.TYPE_CLASS_TEXT })
        view.measure(View.MeasureSpec.makeMeasureSpec(1080, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED))
        view.layout(0, 0, 1080, view.measuredHeight)
        return controller to view
    }

    @Test fun `rapid taps commit every letter without editor queries or rebuilding keys`() {
        val ic = object : FakeInputConnection() {
            override fun getSelectedText(flags: Int): CharSequence? = error("Typing must not query selection")
            override fun getTextBeforeCursor(n: Int, flags: Int): CharSequence? = error("Typing must not read text")
            override fun getTextAfterCursor(n: Int, flags: Int): CharSequence? = error("Typing must not read text")
        }
        val (_, view) = keyboard(ic)
        val original = keys(view).associateBy { it.tag as String }
        val typed = "testeabcdefghijklmnopqrstuvwxyz".repeat(3)
        for (letter in typed) {
            val key = original.getValue(letter.toString())
            touch(key, MotionEvent.ACTION_DOWN)
            touch(key, MotionEvent.ACTION_UP)
        }
        assertEquals(typed, ic.currentText)
        keys(view).forEach { assertSame(original[it.tag], it) }
        view.dismissPopup()
    }

    @Test fun `holding c inserts cedilla on release without a second tap`() {
        val ic = FakeInputConnection()
        val (_, view) = keyboard(ic)
        val key = keys(view).first { it.tag == "c" }
        touch(key, MotionEvent.ACTION_DOWN)
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(ViewConfiguration.getLongPressTimeout() + 1L))
        assertEquals("", ic.currentText)
        touch(key, MotionEvent.ACTION_UP)
        assertEquals("ç", ic.currentText)
        view.dismissPopup()
    }

    @Test fun `overlapping two finger taps reach both keys`() {
        val ic = FakeInputConnection()
        val (_, view) = keyboard(ic)
        val letters = keys(view).associateBy { it.tag as String }
        fun coordinates(letter: String): MotionEvent.PointerCoords {
            val key = letters.getValue(letter)
            var x = key.width / 2f
            var y = key.height / 2f
            var ancestor: View = key
            while (ancestor !== view) {
                x += ancestor.left
                y += ancestor.top
                ancestor = ancestor.parent as View
            }
            return MotionEvent.PointerCoords().apply {
                this.x = x
                this.y = y
                pressure = 1f
                size = 1f
            }
        }
        val first = MotionEvent.PointerProperties().apply { id = 0; toolType = MotionEvent.TOOL_TYPE_FINGER }
        val second = MotionEvent.PointerProperties().apply { id = 1; toolType = MotionEvent.TOOL_TYPE_FINGER }
        val time = SystemClock.uptimeMillis()
        fun dispatch(action: Int, properties: Array<MotionEvent.PointerProperties>, coords: Array<MotionEvent.PointerCoords>) {
            val event = MotionEvent.obtain(time, time, action, properties.size, properties, coords,
                0, 0, 1f, 1f, 0, 0, android.view.InputDevice.SOURCE_TOUCHSCREEN, 0)
            try { assertTrue(view.dispatchTouchEvent(event)) } finally { event.recycle() }
        }
        val t = coordinates("t")
        val n = coordinates("n")
        dispatch(MotionEvent.ACTION_DOWN, arrayOf(first), arrayOf(t))
        dispatch(MotionEvent.ACTION_POINTER_DOWN or (1 shl MotionEvent.ACTION_POINTER_INDEX_SHIFT),
            arrayOf(first, second), arrayOf(t, n))
        dispatch(MotionEvent.ACTION_POINTER_UP, arrayOf(first, second), arrayOf(t, n))
        dispatch(MotionEvent.ACTION_UP, arrayOf(second), arrayOf(n))
        assertEquals("tn", ic.currentText)
        view.dismissPopup()
    }

    @Test fun `holding sliding and releasing selects another accent`() {
        val ic = FakeInputConnection()
        val (_, view) = keyboard(ic)
        val key = keys(view).first { it.tag == "c" }
        touch(key, MotionEvent.ACTION_DOWN)
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(ViewConfiguration.getLongPressTimeout() + 1L))
        touch(key, MotionEvent.ACTION_MOVE, 10000f)
        touch(key, MotionEvent.ACTION_UP, 10000f)
        assertEquals("č", ic.currentText)
        view.dismissPopup()
    }

    @Test fun `cancelled letter does not commit and shift retains held key views`() {
        val ic = FakeInputConnection()
        val (controller, view) = keyboard(ic)
        val key = keys(view).first { it.tag == "a" }
        touch(key, MotionEvent.ACTION_DOWN)
        touch(key, MotionEvent.ACTION_CANCEL)
        assertEquals("", ic.currentText)
        controller.toggleShift()
        touch(key, MotionEvent.ACTION_DOWN)
        touch(key, MotionEvent.ACTION_UP)
        assertEquals("A", ic.currentText)
        assertEquals(ShiftState.OFF, controller.shiftState)
        assertSame(key, keys(view).first { it.tag == "a" })
        view.dismissPopup()
    }

    @Test fun `idle toolbar contains only fixed tools and survives typing`() {
        val ic = FakeInputConnection()
        val (_, view) = keyboard(ic)
        val toolbar = (view.getChildAt(0) as FrameLayout).getChildAt(0) as LinearLayout
        val labels = (0 until toolbar.childCount).map { toolbar.getChildAt(it) }.filterIsInstance<TextView>()
        assertEquals(listOf(context.getString(R.string.correct_action), context.getString(R.string.translate_action)),
            labels.map { it.text.toString() })
        val key = keys(view).first { it.tag == "t" }
        touch(key, MotionEvent.ACTION_DOWN)
        touch(key, MotionEvent.ACTION_UP)
        assertSame(toolbar, (view.getChildAt(0) as FrameLayout).getChildAt(0))
        view.dismissPopup()
    }
}
