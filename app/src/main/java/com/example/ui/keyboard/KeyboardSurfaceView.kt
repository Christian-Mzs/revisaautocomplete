package com.example.ui.keyboard

import android.content.Context
import android.graphics.RectF
import android.view.MotionEvent
import android.view.View
import android.widget.LinearLayout

/** Keeps the existing drawing/layout, but owns the complete functional key surface. */
internal class KeyboardSurfaceView(context: Context) : LinearLayout(context) {
    data class Target(val view: View, val visualBounds: RectF)
    private val targets = ArrayList<Target>()
    var visualHorizontalInsetPx = 0
    var centralized = true
    var onPointerEvent: ((MotionEvent, View?) -> Unit)? = null
    var onGeometryInvalidated: (() -> Unit)? = null
    var isKey: (View) -> Boolean = { false }

    override fun onLayout(changed: Boolean, l: Int, t: Int, r: Int, b: Int) {
        super.onLayout(changed, l, t, r, b)
        targets.clear()
        for (i in 0 until childCount) {
            val row = getChildAt(i) as? LinearLayout ?: continue
            val rowTargets = ArrayList<Target>()
            for (j in 0 until row.childCount) {
                val key = row.getChildAt(j)
                if (isKey(key)) rowTargets.add(Target(key, RectF(
                    (row.left + key.left + visualHorizontalInsetPx).toFloat(), (row.top + key.top).toFloat(),
                    (row.left + key.right - visualHorizontalInsetPx).toFloat(), (row.top + key.bottom).toFloat())))
            }
            targets.addAll(rowTargets)
        }
        // Explicit geometric tie order: upper rectangle, then left rectangle,
        // then bottom/right edges. Distinct keys in this layout never share a rectangle.
        targets.sortWith(compareBy<Target>({ it.visualBounds.top }, { it.visualBounds.left },
            { it.visualBounds.bottom }, { it.visualBounds.right }))
    }

    /** Visual containment wins. Only gaps use squared distance to the rectangle in 2D.
     * Exact distance ties use the geometric order above, independently of child order.
     * Every finite point in [0,width) × [0,height) resolves when keys are laid out.
     * External keyboard padding is excluded. RectF uses half-open containment. */
    fun hitTest(x: Float, y: Float): View? {
        if (!x.isFinite() || !y.isFinite() || x < 0 || y < 0 || x >= width || y >= height) return null
        targets.firstOrNull { it.visualBounds.contains(x, y) }?.let { return it.view }
        var nearest: Target? = null
        var minimum = Float.POSITIVE_INFINITY
        for (target in targets) {
            val bounds = target.visualBounds
            val dx = maxOf(bounds.left - x, 0f, x - bounds.right)
            val dy = maxOf(bounds.top - y, 0f, y - bounds.bottom)
            val distance = dx * dx + dy * dy
            if (distance < minimum) {
                minimum = distance
                nearest = target
            }
        }
        return nearest?.view
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
