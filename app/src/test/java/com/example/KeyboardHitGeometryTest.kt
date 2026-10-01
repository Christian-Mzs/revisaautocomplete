package com.example

import android.content.Context
import android.view.View
import android.widget.LinearLayout
import androidx.test.core.app.ApplicationProvider
import com.example.ui.keyboard.KeyboardGeometry
import com.example.ui.keyboard.KeyboardSurfaceView
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class KeyboardHitGeometryTest {
    private val context: Context get() = ApplicationProvider.getApplicationContext()

    // Deliberately uses the OLD heights: touch precision must not rely on larger keys.
    private fun fixture(reverse: Boolean = false): Pair<KeyboardSurfaceView, Map<String, View>> {
        val surface = KeyboardSurfaceView(context).apply { isKey = { true }; orientation = LinearLayout.VERTICAL }
        val definitions = listOf(
            listOf("shift" to (0 to 65), "z" to (70 to 115), "x" to (120 to 165),
                "m" to (330 to 375), "backspace" to (380 to 450)),
            listOf("," to (90 to 135), "space" to (140 to 360), "." to (365 to 410),
                "action" to (415 to 450)))
        val keys = mutableMapOf<String, View>()
        definitions.forEach { entries ->
            val row = object : LinearLayout(context) {
                override fun onLayout(changed: Boolean, l: Int, t: Int, r: Int, b: Int) {
                    entries.forEach { (name, edges) ->
                        keys.getValue(name).layout(edges.first, 0, edges.second, 45)
                    }
                }
            }.apply {
                layoutParams = LinearLayout.LayoutParams(450, 45).apply { bottomMargin = 12 }
            }
            (if (reverse) entries.reversed() else entries).forEach { (name, _) ->
                row.addView(View(context).apply { tag = name; keys[name] = this })
            }
            surface.addView(row)
        }
        surface.measure(View.MeasureSpec.makeMeasureSpec(450, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(102, View.MeasureSpec.EXACTLY))
        surface.layout(0, 0, 450, 102)
        return surface to keys
    }

    @Test fun `space owns center edges top bottom and all internal corners`() {
        val (surface, keys) = fixture()
        val points = listOf(250f to 79f, 142f to 79f, 358f to 79f,
            250f to 59f, 250f to 100f, 142f to 59f, 358f to 59f,
            142f to 100f, 358f to 100f)
        points.forEach { (x, y) -> assertSame("space at $x,$y", keys["space"], surface.hitTest(x, y)) }
    }

    @Test fun `lower edges of staggered and wide keys cannot be stolen by bottom row`() {
        val (surface, keys) = fixture()
        for ((name, x) in listOf("x" to 122f, "z" to 113f, "m" to 373f,
            "shift" to 63f, "backspace" to 417f)) {
            assertSame(name, keys[name], surface.hitTest(x, 44f))
        }
        assertSame(keys[","], surface.hitTest(122f, 56f))
        assertSame(keys["."], surface.hitTest(373f, 56f))
        assertSame(keys["action"], surface.hitTest(417f, 56f))
        assertSame(keys["shift"], surface.hitTest(63f, 50f))
    }

    @Test fun `gap ties prefer upper then left regardless of child order`() {
        for (reverse in listOf(false, true)) {
            val (surface, keys) = fixture(reverse)
            repeat(10) {
                assertSame(keys["x"], surface.hitTest(122f, 51f))
                assertSame(keys[","], surface.hitTest(137.5f, 70f))
            }
        }
    }

    @Test fun `every interior pixel belongs to its visual key and surface has no holes`() {
        val (surface, keys) = fixture()
        keys.values.forEach { key ->
            val row = key.parent as View
            for (y in 1 until key.height) for (x in 1 until key.width) {
                assertSame(key, surface.hitTest((row.left + key.left + x).toFloat(),
                    (row.top + key.top + y).toFloat()))
            }
        }
        for (y in 0 until surface.height) for (x in 0 until surface.width) {
            assertNotNull(surface.hitTest(x.toFloat(), y.toFloat()))
        }
        assertNull(surface.hitTest(Float.NaN, 0f))
        assertNull(surface.hitTest(0f, Float.POSITIVE_INFINITY))
    }

    @Test fun `vertical experiment leaves fonts and horizontal dimensions unchanged`() {
        assertEquals(45, KeyboardGeometry.KEY_HEIGHT_DP)
        assertEquals(37, KeyboardGeometry.NUMBER_KEY_HEIGHT_DP)
        assertEquals(31.5f, KeyboardGeometry.LETTER_FONT_SP, 0.001f)
        assertEquals(6, KeyboardGeometry.SIDE_PADDING_DP)
        assertEquals(6, KeyboardGeometry.KEY_HORIZONTAL_GAP_DP)
        assertEquals(12, KeyboardGeometry.KEY_VERTICAL_GAP_DP)
    }
}
