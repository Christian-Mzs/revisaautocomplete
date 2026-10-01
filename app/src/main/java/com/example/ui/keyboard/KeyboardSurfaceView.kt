package com.example.ui.keyboard

import android.content.Context
import android.graphics.RectF
import android.view.MotionEvent
import android.view.View
import android.widget.LinearLayout

/** Keeps the existing drawing/layout, but owns the complete functional key surface. */
internal class KeyboardSurfaceView(context: Context) : LinearLayout(context) {
    data class Target(val view: View, val visualBounds: RectF, val touchBounds: RectF = RectF())
    private val rows = ArrayList<List<Target>>()
    var visualHorizontalInsetPx = 0
    var centralized = true
    var onPointerEvent: ((MotionEvent, View?) -> Unit)? = null
    var onGeometryInvalidated: (() -> Unit)? = null
    var isKey: (View) -> Boolean = { false }

    override fun onLayout(changed: Boolean, l: Int, t: Int, r: Int, b: Int) {
        super.onLayout(changed, l, t, r, b)
        rows.clear()
        for (i in 0 until childCount) {
            val row = getChildAt(i) as? LinearLayout ?: continue
            val targets = ArrayList<Target>()
            for (j in 0 until row.childCount) {
                val key = row.getChildAt(j)
                if (isKey(key)) targets.add(Target(key, RectF(
                    (row.left + key.left + visualHorizontalInsetPx).toFloat(), (row.top + key.top).toFloat(),
                    (row.left + key.right - visualHorizontalInsetPx).toFloat(), (row.top + key.bottom).toFloat())))
            }
            if (targets.isNotEmpty()) rows.add(targets)
        }
        for (i in rows.indices) {
            val row = rows[i]
            val centerY = row[0].visualBounds.centerY()
            val top = if (i == 0) 0f else (rows[i - 1][0].visualBounds.centerY() + centerY) / 2f
            val bottom = if (i == rows.lastIndex) height.toFloat() else
                (centerY + rows[i + 1][0].visualBounds.centerY()) / 2f
            for (j in row.indices) {
                val centerX = row[j].visualBounds.centerX()
                val left = if (j == 0) 0f else (row[j - 1].visualBounds.centerX() + centerX) / 2f
                val right = if (j == row.lastIndex) width.toFloat() else
                    (centerX + row[j + 1].visualBounds.centerX()) / 2f
                row[j].touchBounds.set(left, top, right, bottom)
            }
        }
    }

    /** Row and column midpoint partitions cover [0,width) × [0,height) without holes.
     * Ties belong to the next row/key. External keyboard padding is excluded. */
    fun hitTest(x: Float, y: Float): View? {
        if (!x.isFinite() || !y.isFinite() || x < 0 || y < 0 || x >= width || y >= height || rows.isEmpty()) return null
        var rowIndex = 0
        while (rowIndex < rows.lastIndex) {
            if (y < rows[rowIndex][0].touchBounds.bottom) break
            rowIndex++
        }
        val row = rows[rowIndex]
        var column = 0
        while (column < row.lastIndex) {
            if (x < row[column].touchBounds.right) break
            column++
        }
        return row[column].view
    }

    override fun dispatchTouchEvent(event: MotionEvent): Boolean {
        if (!centralized) return super.dispatchTouchEvent(event)
        com.example.ime.InputMetrics.event(event.actionMasked, event.pointerCount)
        val index = event.actionIndex
        val key = if (event.actionMasked == MotionEvent.ACTION_DOWN ||
            event.actionMasked == MotionEvent.ACTION_POINTER_DOWN) hitTest(event.getX(index), event.getY(index)) else null
        onPointerEvent?.invoke(event, key)
        return true
    }

    override fun onDetachedFromWindow() {
        onGeometryInvalidated?.invoke()
        super.onDetachedFromWindow()
    }
}
