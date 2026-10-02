package com.example

import android.content.Context
import android.text.InputType
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.widget.LinearLayout
import android.widget.TextView
import androidx.test.core.app.ApplicationProvider
import com.example.ime.KeyboardController
import com.example.ui.keyboard.KeyboardLayoutView
import kotlinx.coroutines.test.TestScope
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "mdpi")
class KeyboardLayoutCalibrationTest {
    private fun layout(): KeyboardLayoutView {
        val context = ApplicationProvider.getApplicationContext<Context>()
        lateinit var view: KeyboardLayoutView
        val controller = KeyboardController(context, TestScope(), onStateChanged = { view.render() })
        view = KeyboardLayoutView(context, controller)
        controller.updateInputConnection(FakeInputConnection(), EditorInfo().apply { inputType = InputType.TYPE_CLASS_TEXT })
        view.measure(View.MeasureSpec.makeMeasureSpec(415, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED))
        view.layout(0, 0, 415, view.measuredHeight)
        return view
    }
    // Golden landmarks measured from the supplied 691px-wide Samsung capture.
    // Normalize horizontally rather than assuming the screenshot's Android density.
    private val screenshotScale = 691f / 415f
    private fun reference(expected: Float, actual: Float) = assertEquals("Reference landmark $expected px (measured ${actual * screenshotScale} px)", expected, actual * screenshotScale, 3f)

    @Test fun `physical heights and row positions match reference without font scale inflation`() {
        val view = layout()
        val surface = view.getChildAt(1) as LinearLayout
        assertEquals(331, view.height)
        reference(553f, view.height.toFloat())
        val referenceTops = listOf(88f, 171f, 267f, 362f, 458f)
        for (i in referenceTops.indices) {
            val row = surface.getChildAt(i)
            assertEquals(if (i == 0) 37 else 45, row.height)
            reference(referenceTops[i], (surface.top + row.top).toFloat())
        }
        view.dismissPopup()
    }

    @Test fun `staggered letter centers and bottom key rectangles match reference`() {
        val view = layout()
        val surface = view.getChildAt(1) as LinearLayout
        fun descendants(group: ViewGroup): List<View> = (0 until group.childCount).flatMap {
            val child = group.getChildAt(it)
            listOf(child) + if (child is ViewGroup) descendants(child) else emptyList()
        }
        val all = descendants(surface)
        for ((letter, center) in listOf("q" to 43.5f, "p" to 647.5f,
            "z" to 143.5f, "m" to 547.5f)) {
            val key = all.first { it.tag == letter }
            reference(center, surface.left + key.left + key.width / 2f)
        }
        val second = surface.getChildAt(2) as LinearLayout
        val letters = (0 until second.childCount).map { second.getChildAt(it) }.filter { it.tag is String }
        assertEquals(9, letters.size)
        assertEquals(0.35f, com.example.ui.keyboard.KeyboardGeometry.SECOND_ROW_SIDE_INSET_WEIGHT, 0f)
        val expectedWidth = second.width / 9.7f
        letters.forEach { assertEquals(expectedWidth, it.width.toFloat(), 1f) }
        assertEquals(second.width / 2f, (letters.first().left + letters.last().right) / 2f, 1f)
        assertEquals(expectedWidth * 0.35f, letters.first().left.toFloat(), 1f)
        val bottom = surface.getChildAt(4) as LinearLayout
        val expectedEdges = listOf(15f to 105f, 116f to 172f, 183f to 508f,
            519f to 575f, 586f to 676f)
        expectedEdges.forEachIndexed { i, (left, right) ->
            val key = bottom.getChildAt(i)
            reference(left, (surface.left + key.left + 3).toFloat())
            reference(right, (surface.left + key.right - 3).toFloat())
        }
        val letterText = all.filterIsInstance<TextView>().first { it.text.toString() == "q" }
        assertEquals(31.5f, letterText.textSize, 0.001f)
        view.dismissPopup()
    }
}
