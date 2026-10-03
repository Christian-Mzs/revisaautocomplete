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
import com.example.ime.KeyboardMode
import com.example.ui.keyboard.EmojiCatalog
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

    private fun keyboard(ic: FakeInputConnection, width: Int = 1080): Pair<KeyboardController, KeyboardLayoutView> {
        lateinit var view: KeyboardLayoutView
        val controller = KeyboardController(context, TestScope(), onStateChanged = { view.render() })
        view = KeyboardLayoutView(context, controller)
        controller.updateInputConnection(ic, EditorInfo().apply { inputType = InputType.TYPE_CLASS_TEXT })
        view.measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED))
        view.layout(0, 0, width, view.measuredHeight)
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
        view.measure(View.MeasureSpec.makeMeasureSpec(1080, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED))
        view.layout(0, 0, view.measuredWidth, view.measuredHeight)
        assertEquals(5, toolbar.childCount)
        val widths = (0 until toolbar.childCount).map { toolbar.getChildAt(it).width }
        assertTrue(widths.max() - widths.min() <= 1)
        val labels = (0 until toolbar.childCount).map { toolbar.getChildAt(it).contentDescription?.toString() }
        assertTrue(labels.contains(context.getString(R.string.correct_action)))
        assertTrue(labels.contains(context.getString(R.string.translate_action)))
        val key = keys(view).first { it.tag == "t" }
        touch(key, MotionEvent.ACTION_DOWN)
        touch(key, MotionEvent.ACTION_UP)
        assertSame(toolbar, (view.getChildAt(0) as FrameLayout).getChildAt(0))
        view.dismissPopup()
    }

    @Test fun `clipboard pill remains centered with larger dismiss button at right`() {
        val (controller, view) = keyboard(FakeInputConnection())
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
        clipboard.setPrimaryClip(android.content.ClipData.newPlainText("", "Olá mundo"))
        controller.clipboardSuggestion.clipboardChanged()
        view.render()
        view.measure(View.MeasureSpec.makeMeasureSpec(1080, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED))
        view.layout(0, 0, 1080, view.measuredHeight)
        val row = (view.getChildAt(0) as FrameLayout).getChildAt(0) as FrameLayout
        val pill = row.getChildAt(0)
        val close = row.getChildAt(1) as TextView
        assertTrue(kotlin.math.abs((pill.left + pill.right) - row.width) <= 1)
        assertEquals(row.width, close.right)
        assertTrue(pill.right <= close.left)
        assertEquals(39f * context.resources.displayMetrics.scaledDensity, close.textSize, 0.1f)
        close.performClick()
        assertNull(controller.clipboardSuggestion.suggestion)
        assertEquals("Olá mundo", clipboard.primaryClip!!.getItemAt(0).text.toString())
        view.dismissPopup()
    }

    @Test fun `all keyboard modes retain exactly the same height at multiple widths`() {
        val (controller, view) = keyboard(FakeInputConnection())
        for (width in listOf(720, 1080)) {
            val heights = KeyboardMode.entries.map { mode ->
                controller.setMode(mode)
                view.measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
                    View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED))
                view.layout(0, 0, width, view.measuredHeight)
                if (mode != KeyboardMode.EMOJIS) {
                    val rows = view.getChildAt(1) as LinearLayout
                    assertEquals(5, rows.childCount)
                    val last = rows.getChildAt(4)
                    assertEquals(rows.height, last.bottom)
                }
                view.measuredHeight
            }
            assertEquals(1, heights.distinct().size)
        }
        view.dismissPopup()
    }

    @Test fun `emoji button inserts a whole emoji and allows returning to letters`() {
        val ic = FakeInputConnection()
        val (controller, view) = keyboard(ic)
        fun descendants(group: ViewGroup): List<View> = (0 until group.childCount).flatMap {
            val child = group.getChildAt(it)
            listOf(child) + if (child is ViewGroup) descendants(child) else emptyList()
        }
        descendants(view).first { it.contentDescription?.toString() == context.getString(R.string.open_emojis) }.performClick()
        assertEquals(KeyboardMode.EMOJIS, controller.currentMode)
        view.measure(View.MeasureSpec.makeMeasureSpec(1080, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED))
        view.layout(0, 0, view.measuredWidth, view.measuredHeight)
        val emoji = EmojiCatalog.categories.first().emojis.first()
        descendants(view).filterIsInstance<TextView>().first { it.contentDescription?.toString() == emoji }.performClick()
        assertEquals(emoji, ic.currentText)
        controller.handleBackspace()
        assertEquals("", ic.currentText)
        descendants(view).first { it.contentDescription?.toString() == context.getString(R.string.open_emojis) }.performClick()
        assertEquals(KeyboardMode.LETTERS, controller.currentMode)
        view.dismissPopup()
    }

    @Test fun `visual letter gaps belong to the touch target`() {
        val ic = FakeInputConnection()
        val (_, view) = keyboard(ic)
        val key = keys(view).first { it.tag == "t" }
        val params = key.layoutParams as LinearLayout.LayoutParams
        assertEquals(0, params.leftMargin)
        assertEquals(0, params.rightMargin)
        touch(key, MotionEvent.ACTION_DOWN, 0.5f)
        touch(key, MotionEvent.ACTION_UP, 0.5f)
        assertEquals("t", ic.currentText)
        view.dismissPopup()
    }

    @Test fun `sustained typing preserves spaces and characters without waiting for posted clicks`() {
        val ic = object : FakeInputConnection() {
            override fun getTextBeforeCursor(n: Int, flags: Int): CharSequence = error("Typing queried text")
            override fun getTextAfterCursor(n: Int, flags: Int): CharSequence = error("Typing queried text")
            override fun getSelectedText(flags: Int): CharSequence = error("Typing queried selection")
        }
        val (_, view) = keyboard(ic)
        fun descendants(group: ViewGroup): List<View> = (0 until group.childCount).flatMap {
            val child = group.getChildAt(it)
            listOf(child) + if (child is ViewGroup) descendants(child) else emptyList()
        }
        val letterKeys = keys(view).associateBy { it.tag.toString() }
        val space = descendants(view).filterIsInstance<TextView>()
            .first { it.text.toString() == "espaço" }.parent as View
        val expected = "teste de digitacao rapida com muitas palavras ".repeat(100)
        expected.forEach { char ->
            val key = if (char == ' ') space else letterKeys.getValue(char.toString())
            touch(key, MotionEvent.ACTION_DOWN)
            touch(key, MotionEvent.ACTION_UP)
        }
        // No looper idle: each release must have committed before the next touch.
        assertEquals(expected, ic.currentText)
        assertSame(letterKeys.getValue("t"), keys(view).first { it.tag == "t" })
        val toolbar = (view.getChildAt(0) as FrameLayout).getChildAt(0) as LinearLayout
        assertEquals(context.getString(R.string.open_emojis), toolbar.getChildAt(0).contentDescription)
        view.dismissPopup()
    }
    private fun surface(view: KeyboardLayoutView) =
        view.getChildAt(1) as com.example.ui.keyboard.KeyboardSurfaceView

    private fun surfacePoint(key: View, surface: View): Pair<Float, Float> {
        var x = key.width / 2f
        var y = key.height / 2f
        var child = key
        while (child !== surface) {
            x += child.left; y += child.top
            child = child.parent as View
        }
        return x to y
    }

    private fun surfaceEvent(surface: View, action: Int, ids: IntArray, points: List<Pair<Float, Float>>, flags: Int = 0) {
        val time = SystemClock.uptimeMillis()
        val properties = ids.map { id -> MotionEvent.PointerProperties().apply {
            this.id = id; toolType = MotionEvent.TOOL_TYPE_FINGER
        } }.toTypedArray()
        val coordinates = points.map { point -> MotionEvent.PointerCoords().apply {
            x = point.first; y = point.second; pressure = 1f; size = 1f
        } }.toTypedArray()
        val event = MotionEvent.obtain(time, time, action, ids.size, properties, coordinates,
            0, 0, 1f, 1f, 0, 0, android.view.InputDevice.SOURCE_TOUCHSCREEN, flags)
        try { assertTrue(surface.dispatchTouchEvent(event)) } finally { event.recycle() }
    }

    @Test fun `functional surface has no holes including exact boundaries in every non emoji mode`() {
        val (controller, view) = keyboard(FakeInputConnection())
        for (mode in listOf(KeyboardMode.LETTERS, KeyboardMode.NUMBERS, KeyboardMode.SYMBOLS)) {
            controller.setMode(mode)
            for (width in listOf(320, 720, 1080)) {
                view.measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
                    View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED))
                view.layout(0, 0, width, view.measuredHeight)
                val surface = surface(view)
                for (y in 0 until surface.height step 3) for (x in 0 until surface.width step 3) {
                    assertNotNull("Hole at $mode $width $x $y", surface.hitTest(x.toFloat(), y.toFloat()))
                }
                assertNotNull(surface.hitTest(0f, 0f))
                assertNotNull(surface.hitTest(surface.width - 0.01f, surface.height - 0.01f))
                assertNull(surface.hitTest(-1f, 0f))
                assertNull(surface.hitTest(0f, surface.height.toFloat()))
                for (i in 0 until surface.childCount) {
                    val row = surface.getChildAt(i) as LinearLayout
                    val interactive = (0 until row.childCount).map { row.getChildAt(it) }.filter { it.isClickable }
                    for (key in interactive) {
                        val point = surfacePoint(key, surface)
                        assertSame(key, surface.hitTest(point.first, point.second))
                        val rowView = key.parent as View
                        val left = rowView.left + key.left + surface.visualHorizontalInsetPx
                        val right = rowView.left + key.right - surface.visualHorizontalInsetPx
                        val top = rowView.top + key.top
                        val bottom = rowView.top + key.bottom
                        for (y in top + 1 until minOf(bottom, surface.height - (6 * context.resources.displayMetrics.density).toInt()) step 2) for (x in maxOf(left + 1, (3 * context.resources.displayMetrics.density).toInt()) until right step 2) {
                            assertSame("Containment $mode $width ${key.tag} $x,$y", key,
                                surface.hitTest(
                                    x - com.example.ui.keyboard.KeyboardGeometry.TOUCH_BIAS_X_DP * context.resources.displayMetrics.density,
                                    y - com.example.ui.keyboard.KeyboardGeometry.TOUCH_BIAS_Y_DP * context.resources.displayMetrics.density))
                        }
                    }
                    for (pair in interactive.zipWithNext()) {
                        val a = surfacePoint(pair.first, surface)
                        val b = surfacePoint(pair.second, surface)
                        assertNotNull(surface.hitTest((a.first + b.first) / 2, a.second))
                    }
                    if (i < surface.childCount - 1) {
                        val next = surface.getChildAt(i + 1)
                        val boundary = (row.top + row.height / 2f + next.top + next.height / 2f) / 2f
                        for (x in 0 until surface.width step 3) assertNotNull(surface.hitTest(x.toFloat(), boundary))
                    }
                }
            }
        }
        view.dismissPopup()
    }

    @Test fun `200 surface taps produce 200 activations and synchronous accepted commits`() {
        val ic = FakeInputConnection()
        val (_, view) = keyboard(ic)
        val surface = surface(view)
        val letters = keys(view).associateBy { it.tag.toString() }
        com.example.ime.InputMetrics.reset()
        val expected = "al".repeat(100)
        expected.forEach { letter ->
            val point = surfacePoint(letters.getValue(letter.toString()), surface)
            surfaceEvent(surface, MotionEvent.ACTION_DOWN, intArrayOf(7), listOf(point))
            surfaceEvent(surface, MotionEvent.ACTION_UP, intArrayOf(7), listOf(point))
        }
        assertEquals(expected, ic.currentText)
        val counts = com.example.ime.InputMetrics.snapshot()
        for (name in listOf("down", "up", "resolved", "activation", "character", "commitAttempt", "commitAccepted")) {
            assertEquals(name, 200L, counts[name])
        }
        assertEquals(0L, counts["miss"])
        assertEquals(0L, counts["abandoned"])
        view.dismissPopup()
    }

    @Test fun `independent non sequential pointer ids preserve release order including same key`() {
        for ((a, b) in listOf("q" to "p", "a" to "l", "c" to "n", "a" to "a")) {
            for (firstReleased in listOf(0, 1)) {
                val ic = FakeInputConnection()
                val (_, view) = keyboard(ic)
                val surface = surface(view)
                val letters = keys(view).associateBy { it.tag.toString() }
                val pa = surfacePoint(letters.getValue(a), surface)
                val pb = surfacePoint(letters.getValue(b), surface)
                repeat(100) {
                    surfaceEvent(surface, MotionEvent.ACTION_DOWN, intArrayOf(7), listOf(pa))
                    surfaceEvent(surface, MotionEvent.ACTION_POINTER_DOWN or (1 shl MotionEvent.ACTION_POINTER_INDEX_SHIFT),
                        intArrayOf(7, 23), listOf(pa, pb))
                    surfaceEvent(surface, MotionEvent.ACTION_POINTER_UP or (firstReleased shl MotionEvent.ACTION_POINTER_INDEX_SHIFT),
                        intArrayOf(7, 23), listOf(pa, pb))
                    surfaceEvent(surface, MotionEvent.ACTION_UP, intArrayOf(if (firstReleased == 0) 23 else 7),
                        listOf(if (firstReleased == 0) pb else pa))
                }
                assertEquals((if (firstReleased == 0) a + b else b + a).repeat(100), ic.currentText)
                view.dismissPopup()
            }
        }
    }

    @Test fun `surface cancel removes all pointers timers and leaves next tap working`() {
        val ic = FakeInputConnection()
        val (_, view) = keyboard(ic)
        val surface = surface(view)
        val letters = keys(view).associateBy { it.tag.toString() }
        val a = surfacePoint(letters.getValue("a"), surface)
        val c = surfacePoint(letters.getValue("c"), surface)
        surfaceEvent(surface, MotionEvent.ACTION_DOWN, intArrayOf(7), listOf(a))
        surfaceEvent(surface, MotionEvent.ACTION_POINTER_DOWN or (1 shl MotionEvent.ACTION_POINTER_INDEX_SHIFT),
            intArrayOf(7, 23), listOf(a, c))
        surfaceEvent(surface, MotionEvent.ACTION_CANCEL, intArrayOf(7, 23), listOf(a, c))
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(1000))
        assertEquals("", ic.currentText)
        assertFalse(letters.getValue("a").isPressed)
        assertFalse(letters.getValue("c").isPressed)
        surfaceEvent(surface, MotionEvent.ACTION_DOWN, intArrayOf(23), listOf(c))
        surfaceEvent(surface, MotionEvent.ACTION_UP, intArrayOf(23), listOf(c))
        assertEquals("c", ic.currentText)
        view.dismissPopup()
    }

    @Test fun `held accent survives another pointer tap`() {
        val ic = FakeInputConnection()
        val (_, view) = keyboard(ic)
        val surface = surface(view)
        val letters = keys(view).associateBy { it.tag.toString() }
        val c = surfacePoint(letters.getValue("c"), surface)
        val l = surfacePoint(letters.getValue("l"), surface)
        surfaceEvent(surface, MotionEvent.ACTION_DOWN, intArrayOf(7), listOf(c))
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(ViewConfiguration.getLongPressTimeout() + 1L))
        surfaceEvent(surface, MotionEvent.ACTION_POINTER_DOWN or (1 shl MotionEvent.ACTION_POINTER_INDEX_SHIFT),
            intArrayOf(7, 23), listOf(c, l))
        surfaceEvent(surface, MotionEvent.ACTION_POINTER_UP or (1 shl MotionEvent.ACTION_POINTER_INDEX_SHIFT),
            intArrayOf(7, 23), listOf(c, l))
        surfaceEvent(surface, MotionEvent.ACTION_UP, intArrayOf(7), listOf(c))
        assertEquals("lç", ic.currentText)
        view.dismissPopup()
    }

    @Test fun `backspace repeat belongs to its pointer and stops on cancellation and detach cleanup`() {
        val ic = FakeInputConnection("abcdef")
        val (_, view) = keyboard(ic)
        val surface = surface(view)
        val row = surface.getChildAt(3) as LinearLayout
        val backspace = row.getChildAt(row.childCount - 1)
        val a = surfacePoint(keys(view).first { it.tag == "a" }, surface)
        val back = surfacePoint(backspace, surface)
        surfaceEvent(surface, MotionEvent.ACTION_DOWN, intArrayOf(7), listOf(back))
        assertEquals("abcde", ic.currentText)
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(351))
        assertEquals("abcd", ic.currentText)
        surfaceEvent(surface, MotionEvent.ACTION_POINTER_DOWN or (1 shl MotionEvent.ACTION_POINTER_INDEX_SHIFT),
            intArrayOf(7, 23), listOf(back, a))
        surfaceEvent(surface, MotionEvent.ACTION_POINTER_UP or (1 shl MotionEvent.ACTION_POINTER_INDEX_SHIFT),
            intArrayOf(7, 23), listOf(back, a))
        assertEquals("abcda", ic.currentText)
        surfaceEvent(surface, MotionEvent.ACTION_UP, intArrayOf(7), listOf(back))
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(1000))
        assertEquals("abcda", ic.currentText)
        surfaceEvent(surface, MotionEvent.ACTION_DOWN, intArrayOf(7), listOf(back))
        view.dismissPopup()
        val stopped = ic.currentText
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(1000))
        assertEquals(stopped, ic.currentText)
    }

    @Test fun `cancelled pointer up leaves the other pointer active`() {
        val ic = FakeInputConnection()
        val (_, view) = keyboard(ic)
        val surface = surface(view)
        val letters = keys(view).associateBy { it.tag.toString() }
        val a = surfacePoint(letters.getValue("a"), surface)
        val l = surfacePoint(letters.getValue("l"), surface)
        surfaceEvent(surface, MotionEvent.ACTION_DOWN, intArrayOf(7), listOf(a))
        surfaceEvent(surface, MotionEvent.ACTION_POINTER_DOWN or (1 shl MotionEvent.ACTION_POINTER_INDEX_SHIFT),
            intArrayOf(7, 23), listOf(a, l))
        surfaceEvent(surface, MotionEvent.ACTION_POINTER_UP, intArrayOf(7, 23), listOf(a, l), MotionEvent.FLAG_CANCELED)
        assertEquals("", ic.currentText)
        assertTrue(letters.getValue("l").isPressed)
        surfaceEvent(surface, MotionEvent.ACTION_UP, intArrayOf(23), listOf(l))
        assertEquals("l", ic.currentText)
        view.dismissPopup()
    }

    @Test fun `root dispatch never delegates ordinary surface touches to child listeners`() {
        val ic = FakeInputConnection()
        val (_, view) = keyboard(ic)
        val surface = surface(view)
        val key = keys(view).first { it.tag == "t" }
        key.setOnTouchListener { _, _ -> error("Child intercepted central input") }
        val row = key.parent as View
        row.setOnTouchListener { _, _ -> error("Row intercepted central input") }
        val point = surfacePoint(key, surface)
        val rootPoint = point.first + surface.left to point.second + surface.top
        surfaceEvent(view, MotionEvent.ACTION_DOWN, intArrayOf(7), listOf(rootPoint))
        surfaceEvent(view, MotionEvent.ACTION_UP, intArrayOf(7), listOf(rootPoint))
        assertEquals("t", ic.currentText)
        view.dismissPopup()
    }

    @Test fun `touches in former spacers and row gaps really commit`() {
        val ic = FakeInputConnection()
        val (_, view) = keyboard(ic)
        val surface = surface(view)
        val letters = keys(view).associateBy { it.tag.toString() }
        val a = surfacePoint(letters.getValue("a"), surface)
        val l = surfacePoint(letters.getValue("l"), surface)
        val g = surfacePoint(letters.getValue("g"), surface)
        val h = surfacePoint(letters.getValue("h"), surface)
        val t = surfacePoint(letters.getValue("t"), surface)
        val points = listOf(0f to a.second, surface.width - 0.01f to l.second,
            (g.first + h.first) / 2f to g.second,
            t.first to (t.second + g.second) / 2f)
        val expected = StringBuilder()
        points.forEach { point ->
            val target = surface.hitTest(point.first, point.second)!!
            expected.append(target.tag as String)
            surfaceEvent(view, MotionEvent.ACTION_DOWN, intArrayOf(7),
                listOf(point.first + surface.left to point.second + surface.top))
            surfaceEvent(view, MotionEvent.ACTION_UP, intArrayOf(7),
                listOf(point.first + surface.left to point.second + surface.top))
        }
        assertEquals(expected.toString(), ic.currentText)
        view.dismissPopup()
    }

    @Test fun `diagnostics distinguish a confirmed touch from an editor rejecting commit`() {
        val ic = object : FakeInputConnection() {
            override fun commitText(text: CharSequence?, newCursorPosition: Int) = false
        }
        val (_, view) = keyboard(ic)
        val surface = surface(view)
        val point = surfacePoint(keys(view).first { it.tag == "t" }, surface)
        com.example.ime.InputMetrics.reset()
        surfaceEvent(surface, MotionEvent.ACTION_DOWN, intArrayOf(7), listOf(point))
        surfaceEvent(surface, MotionEvent.ACTION_UP, intArrayOf(7), listOf(point))
        val counts = com.example.ime.InputMetrics.snapshot()
        assertEquals(1L, counts["activation"])
        assertEquals(1L, counts["commitAttempt"])
        assertEquals(1L, counts["commitRejected"])
        assertEquals(0L, counts["commitAccepted"])
        assertEquals("", ic.currentText)
        view.dismissPopup()
    }

    @Test fun `Q A Z E centers keep their letters`() {
        val ic = FakeInputConnection()
        val (_, view) = keyboard(ic)
        val surface = surface(view)
        for (letter in listOf("q", "a", "z", "e")) {
            val key = keys(view).first { it.tag == letter }
            val point = surfacePoint(key, surface)
            assertSame(key, surface.hitTest(point.first, point.second))
            surfaceEvent(surface, MotionEvent.ACTION_DOWN, intArrayOf(7), listOf(point))
            surfaceEvent(surface, MotionEvent.ACTION_UP, intArrayOf(7), listOf(point))
        }
        assertEquals("qaze", ic.currentText)
        view.dismissPopup()
    }

    @Test fun `micro slides and shallow releases keep the DOWN key once`() {
        for ((start, end) in listOf("a" to "s", "q" to "w", "z" to "x")) {
            for (small in listOf(true, false)) {
                val ic = FakeInputConnection()
                val (_, view) = keyboard(ic)
                val surface = surface(view)
                val letters = keys(view).associateBy { it.tag.toString() }
                val initial = letters.getValue(start)
                val destination = letters.getValue(end)
                val center = surfacePoint(initial, surface)
                val endCenter = surfacePoint(destination, surface)
                // Locate the normal hit-test boundary, including the visual gap.
                var left = center.first
                var right = endCenter.first
                repeat(24) {
                    val middle = (left + right) / 2f
                    if (surface.hitTest(middle, center.second) === initial) left = middle else right = middle
                }
                val density = context.resources.displayMetrics.density
                val down = if (small) (left - density) to center.second else center
                val up = (right + density) to center.second
                assertSame(initial, surface.hitTest(down.first, down.second))
                assertSame(destination, surface.hitTest(up.first, up.second))
                assertFalse(surface.isInsideRetargetArea(destination, up.first, up.second))
                if (small) assertTrue(up.first - down.first < 10f * density)
                else assertTrue(up.first - down.first > 10f * density)
                com.example.ime.InputMetrics.reset()
                surfaceEvent(surface, MotionEvent.ACTION_DOWN, intArrayOf(7), listOf(down))
                surfaceEvent(surface, MotionEvent.ACTION_MOVE, intArrayOf(7), listOf(up))
                surfaceEvent(surface, MotionEvent.ACTION_UP, intArrayOf(7), listOf(up))
                surfaceEvent(surface, MotionEvent.ACTION_UP, intArrayOf(7), listOf(up))
                assertEquals(start, ic.currentText)
                assertEquals(1L, com.example.ime.InputMetrics.snapshot()["activation"])
                view.dismissPopup()
            }
        }
    }


    @Test @Config(qualifiers = "mdpi")
    fun `retarget requires strictly more than ten dp even inside destination`() {
        for (distance in listOf(10f, 11f)) {
            val ic = FakeInputConnection()
            val (_, view) = keyboard(ic, width = 240)
            val surface = surface(view)
            val initial = keys(view).first { it.tag == "a" }
            val destination = keys(view).first { it.tag == "s" }
            val center = surfacePoint(initial, surface)
            var left = center.first
            var right = surfacePoint(destination, surface).first
            repeat(24) {
                val middle = (left + right) / 2f
                if (surface.hitTest(middle, center.second) === initial) left = middle else right = middle
            }
            // Integer coordinates make the exactly-10px case unambiguous at mdpi.
            val up = (kotlin.math.ceil(right) + 7f) to center.second
            val down = (up.first - distance) to center.second
            assertSame(initial, surface.hitTest(down.first, down.second))
            assertSame(destination, surface.hitTest(up.first, up.second))
            assertTrue(surface.isInsideRetargetArea(destination, up.first, up.second))
            surfaceEvent(surface, MotionEvent.ACTION_DOWN, intArrayOf(7), listOf(down))
            surfaceEvent(surface, MotionEvent.ACTION_UP, intArrayOf(7), listOf(up))
            assertEquals(if (distance == 10f) "a" else "s", ic.currentText)
            view.dismissPopup()
        }
    }

    @Test fun `deliberate slide from A deeply into S retargets once`() {
        val ic = FakeInputConnection()
        val (_, view) = keyboard(ic)
        val surface = surface(view)
        val down = surfacePoint(keys(view).first { it.tag == "a" }, surface)
        val destination = keys(view).first { it.tag == "s" }
        val up = surfacePoint(destination, surface)
        assertTrue(up.first - down.first > 10f * context.resources.displayMetrics.density)
        assertTrue(surface.isInsideRetargetArea(destination, up.first, up.second))
        com.example.ime.InputMetrics.reset()
        surfaceEvent(surface, MotionEvent.ACTION_DOWN, intArrayOf(7), listOf(down))
        surfaceEvent(surface, MotionEvent.ACTION_MOVE, intArrayOf(7), listOf(up))
        surfaceEvent(surface, MotionEvent.ACTION_UP, intArrayOf(7), listOf(up))
        assertEquals("s", ic.currentText)
        assertEquals(1L, com.example.ime.InputMetrics.snapshot()["activation"])
        view.dismissPopup()
    }

    @Test fun `release retargets normal keys once including a shifted row`() {
        for ((start, end) in listOf("w" to "e", "d" to "r", "x" to "c")) {
            val ic = FakeInputConnection()
            val (_, view) = keyboard(ic)
            val surface = surface(view)
            val letters = keys(view).associateBy { it.tag.toString() }
            val down = surfacePoint(letters.getValue(start), surface)
            val up = surfacePoint(letters.getValue(end), surface)
            com.example.ime.InputMetrics.reset()
            surfaceEvent(surface, MotionEvent.ACTION_DOWN, intArrayOf(7), listOf(down))
            assertEquals("", ic.currentText)
            surfaceEvent(surface, MotionEvent.ACTION_UP, intArrayOf(7), listOf(up))
            surfaceEvent(surface, MotionEvent.ACTION_UP, intArrayOf(7), listOf(up))
            assertEquals(end, ic.currentText)
            assertEquals(1L, com.example.ime.InputMetrics.snapshot()["activation"])
            assertFalse(letters.getValue(start).isPressed)
            view.dismissPopup()
        }
    }

    @Test fun `accent release over another key keeps selected accent`() {
        val ic = FakeInputConnection()
        val (_, view) = keyboard(ic)
        val surface = surface(view)
        val c = surfacePoint(keys(view).first { it.tag == "c" }, surface)
        val v = surfacePoint(keys(view).first { it.tag == "v" }, surface)
        surfaceEvent(surface, MotionEvent.ACTION_DOWN, intArrayOf(7), listOf(c))
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(ViewConfiguration.getLongPressTimeout() + 1L))
        surfaceEvent(surface, MotionEvent.ACTION_UP, intArrayOf(7), listOf(v))
        assertEquals("ç", ic.currentText)
        view.dismissPopup()
    }

    @Test fun `backspace release over a letter does not retarget or delete again`() {
        val ic = FakeInputConnection("abcdef")
        val (_, view) = keyboard(ic)
        val surface = surface(view)
        val row = surface.getChildAt(3) as LinearLayout
        val back = surfacePoint(row.getChildAt(row.childCount - 1), surface)
        val a = surfacePoint(keys(view).first { it.tag == "a" }, surface)
        surfaceEvent(surface, MotionEvent.ACTION_DOWN, intArrayOf(7), listOf(back))
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(351))
        assertEquals("abcd", ic.currentText)
        surfaceEvent(surface, MotionEvent.ACTION_UP, intArrayOf(7), listOf(a))
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(1000))
        assertEquals("abcd", ic.currentText)
        view.dismissPopup()
    }

    @Test fun `outside or cancelled release never commits a retargeted letter`() {
        for ((point, flags) in listOf((-1f to 20f) to 0,
            (Float.NaN to 20f) to 0, (100f to 100f) to MotionEvent.FLAG_CANCELED)) {
            val ic = FakeInputConnection()
            val (_, view) = keyboard(ic)
            val surface = surface(view)
            val a = surfacePoint(keys(view).first { it.tag == "a" }, surface)
            surfaceEvent(surface, MotionEvent.ACTION_DOWN, intArrayOf(7), listOf(a))
            surfaceEvent(surface, MotionEvent.ACTION_UP, intArrayOf(7), listOf(point), flags)
            assertEquals("", ic.currentText)
            view.dismissPopup()
        }
    }

}

