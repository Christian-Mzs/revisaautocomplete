package com.example

import android.content.Context
import android.os.SystemClock
import android.view.MotionEvent
import android.view.View
import android.widget.FrameLayout
import androidx.test.core.app.ApplicationProvider
import com.example.ui.keyboard.EmojiSwipeGrid
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class EmojiSwipeGridTest {
    @Test fun `empty recent grid still accepts left and right swipes`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val directions = mutableListOf<Int>()
        val grid = EmojiSwipeGrid(context) { directions.add(it) }
        val parent = FrameLayout(context).apply { addView(grid) }
        parent.measure(View.MeasureSpec.makeMeasureSpec(600, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(300, View.MeasureSpec.EXACTLY))
        parent.layout(0, 0, 600, 300)
        fun dispatch(action: Int, x: Float) {
            val time = SystemClock.uptimeMillis()
            val event = MotionEvent.obtain(time, time, action, x, 100f, 0)
            try { assertTrue(grid.dispatchTouchEvent(event)) } finally { event.recycle() }
        }
        dispatch(MotionEvent.ACTION_DOWN, 500f)
        dispatch(MotionEvent.ACTION_MOVE, 100f)
        dispatch(MotionEvent.ACTION_UP, 100f)
        dispatch(MotionEvent.ACTION_DOWN, 100f)
        dispatch(MotionEvent.ACTION_MOVE, 500f)
        dispatch(MotionEvent.ACTION_UP, 500f)
        assertEquals(listOf(1, -1), directions)
        dispatch(MotionEvent.ACTION_DOWN, 500f)
        dispatch(MotionEvent.ACTION_MOVE, 100f)
        dispatch(MotionEvent.ACTION_CANCEL, 100f)
        assertEquals(listOf(1, -1), directions)
    }
}
