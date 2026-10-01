package com.example.ui.keyboard

import android.content.Context
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import kotlin.math.abs

/** Real neighboring pages follow the finger; vertical gestures remain with each emoji grid. */
internal class EmojiCategoryPager(context: Context, private val changed: (Int) -> Unit) : HorizontalScrollView(context) {
    private val pages = LinearLayout(context).apply { orientation = LinearLayout.HORIZONTAL }
    private var page = 0
    private var startX = 0f
    private var startY = 0f
    private var horizontal = false
    private val slop = ViewConfiguration.get(context).scaledTouchSlop
    init { isHorizontalScrollBarEnabled = false; isFillViewport = true; addView(pages) }
    fun addPage(view: View) { pages.addView(view, LinearLayout.LayoutParams(1, LayoutParams.MATCH_PARENT)) }
    fun selectPage(index: Int, animate: Boolean = true) {
        page = index.coerceIn(0, (pages.childCount - 1).coerceAtLeast(0))
        changed(page)
        if (animate) smoothScrollTo(page * width, 0) else scrollTo(page * width, 0)
    }
    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val pageWidth = View.MeasureSpec.getSize(widthMeasureSpec)
        for (i in 0 until pages.childCount) {
            val child = pages.getChildAt(i)
            if (child.layoutParams.width != pageWidth) {
                child.layoutParams = LinearLayout.LayoutParams(pageWidth, LayoutParams.MATCH_PARENT)
            }
        }
        super.onMeasure(widthMeasureSpec, heightMeasureSpec)
    }
    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        post { scrollTo(page * w, 0) }
    }
    override fun requestDisallowInterceptTouchEvent(disallow: Boolean) {
        // A grid must not swallow a horizontal gesture, including an empty Recentes page.
        if (!disallow) super.requestDisallowInterceptTouchEvent(false)
    }
    override fun onInterceptTouchEvent(event: MotionEvent): Boolean {
        if (event.actionMasked == MotionEvent.ACTION_DOWN) {
            startX = event.x; startY = event.y; horizontal = false
            super.onInterceptTouchEvent(event)
            return false
        }
        if (event.actionMasked == MotionEvent.ACTION_MOVE) {
            val dx = abs(event.x - startX); val dy = abs(event.y - startY)
            if (dx > slop && dx > dy * 1.5f) horizontal = true
            if (!horizontal) return false
        }
        return super.onInterceptTouchEvent(event) || horizontal
    }
    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (event.actionMasked == MotionEvent.ACTION_DOWN) {
            startX = event.x; startY = event.y; horizontal = false
        } else if (event.actionMasked == MotionEvent.ACTION_MOVE) {
            val dx = abs(event.x - startX); val dy = abs(event.y - startY)
            if (dx > slop && dx > dy * 1.5f) horizontal = true
        }
        val handled = super.onTouchEvent(event)
        if (event.actionMasked == MotionEvent.ACTION_UP || event.actionMasked == MotionEvent.ACTION_CANCEL) {
            // Stop the platform fling so it cannot carry us beyond the chosen page.
            fling(0)
            // A deliberate drag of 20% is enough; no need to cross half the display.
            val dx = event.x - startX
            val threshold = maxOf(slop * 3f, width * 0.20f)
            val target = if (event.actionMasked == MotionEvent.ACTION_UP && horizontal && abs(dx) >= threshold)
                page + if (dx < 0) 1 else -1 else page
            selectPage(target)
            horizontal = false
        }
        return handled
    }
}
