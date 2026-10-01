package com.example

import android.content.Context
import android.os.SystemClock
import android.view.MotionEvent
import android.view.View
import android.widget.FrameLayout
import android.widget.GridView
import androidx.test.core.app.ApplicationProvider
import com.example.ui.keyboard.EmojiCategoryPager
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class EmojiSwipeGridTest {
    @Test fun `neighboring categories move continuously and empty recent page is navigable`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val selected = mutableListOf<Int>()
        val pager = EmojiCategoryPager(context) { selected.add(it) }
        repeat(3) { pager.addPage(GridView(context)) }
        val parent = FrameLayout(context).apply { addView(pager) }
        parent.measure(View.MeasureSpec.makeMeasureSpec(600, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(300, View.MeasureSpec.EXACTLY))
        parent.layout(0, 0, 600, 300)
        fun dispatch(action: Int, x: Float, y: Float = 100f) {
            val time = SystemClock.uptimeMillis()
            val event = MotionEvent.obtain(time, time, action, x, y, 0)
            try { pager.dispatchTouchEvent(event) } finally { event.recycle() }
        }
        dispatch(MotionEvent.ACTION_DOWN, 500f)
        dispatch(MotionEvent.ACTION_MOVE, 450f)
        dispatch(MotionEvent.ACTION_MOVE, 150f)
        assertTrue(pager.scrollX > 0)
        assertTrue(pager.scrollX < 600)
        dispatch(MotionEvent.ACTION_UP, 150f)
        assertEquals(1, selected.last())
        pager.selectPage(0, false)
        assertEquals(0, pager.scrollX)
        dispatch(MotionEvent.ACTION_DOWN, 100f)
        dispatch(MotionEvent.ACTION_MOVE, 140f)
        dispatch(MotionEvent.ACTION_MOVE, 250f)
        dispatch(MotionEvent.ACTION_UP, 250f)
        assertEquals(0, selected.last()) // at the first page, outward drag remains clamped
        dispatch(MotionEvent.ACTION_DOWN, 400f)
        dispatch(MotionEvent.ACTION_MOVE, 360f)
        dispatch(MotionEvent.ACTION_MOVE, 250f)
        dispatch(MotionEvent.ACTION_UP, 250f)
        assertEquals(1, selected.last()) // only 25% of the viewport, not half
        pager.selectPage(2, false)
        assertEquals(1200, pager.scrollX)
        pager.selectPage(0, false)
        dispatch(MotionEvent.ACTION_DOWN, 200f)
        dispatch(MotionEvent.ACTION_MOVE, 205f, 250f)
        dispatch(MotionEvent.ACTION_CANCEL, 205f, 250f)
        assertEquals(0, selected.last())
    }
}
