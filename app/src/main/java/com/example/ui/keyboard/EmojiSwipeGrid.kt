package com.example.ui.keyboard

import android.content.Context
import android.view.MotionEvent
import android.view.ViewConfiguration
import android.widget.GridView
import kotlin.math.abs

/** Intercepts horizontal swipes, cancelling pressed emoji keys before changing pages. */
internal class EmojiSwipeGrid(context: Context, private val changeCategory: (Int) -> Unit) : GridView(context) {
    private var startX = 0f
    private var startY = 0f
    private var swiping = false
    private val threshold = ViewConfiguration.get(context).scaledTouchSlop * 3

    override fun onInterceptTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                startX = event.x
                startY = event.y
                swiping = false
            }
            MotionEvent.ACTION_MOVE -> {
                val dx = event.x - startX
                val dy = event.y - startY
                if (abs(dx) > threshold && abs(dx) > abs(dy) * 1.5f) {
                    swiping = true
                    parent?.requestDisallowInterceptTouchEvent(true)
                    return true
                }
            }
        }
        return super.onInterceptTouchEvent(event)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (event.actionMasked == MotionEvent.ACTION_DOWN) {
            startX = event.x
            startY = event.y
            swiping = false
            super.onTouchEvent(event)
            // Empty Recentes has no children: own DOWN so later MOVE/UP still arrive.
            return true
        }
        if (!swiping && event.actionMasked == MotionEvent.ACTION_MOVE) {
            val dx = event.x - startX
            val dy = event.y - startY
            if (abs(dx) > threshold && abs(dx) > abs(dy) * 1.5f) {
                swiping = true
                parent?.requestDisallowInterceptTouchEvent(true)
            }
        }
        if (!swiping) { super.onTouchEvent(event); return true }
        if (event.actionMasked == MotionEvent.ACTION_UP) {
            swiping = false
            parent?.requestDisallowInterceptTouchEvent(false)
            if (abs(event.x - startX) > threshold) changeCategory(if (event.x < startX) 1 else -1)
        } else if (event.actionMasked == MotionEvent.ACTION_CANCEL) {
            swiping = false
            parent?.requestDisallowInterceptTouchEvent(false)
        }
        return true
    }
}
