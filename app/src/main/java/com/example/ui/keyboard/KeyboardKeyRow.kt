package com.example.ui.keyboard

import android.content.Context
import android.view.View
import android.widget.LinearLayout
import kotlin.math.roundToInt

/** Rounds cumulative boundaries, avoiding drift toward the last weighted key. */
internal class KeyboardKeyRow(context: Context) : LinearLayout(context) {
    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec)
        if (orientation != HORIZONTAL || childCount == 0) return
        val children = (0 until childCount).map { getChildAt(it) }
        val params = children.map { it.layoutParams as LayoutParams }
        // These rows consist exclusively of weighted keys and weighted spacers.
        if (children.any { it.visibility == View.GONE } || params.any { it.weight <= 0f }) return
        val available = measuredWidth - paddingLeft - paddingRight - params.sumOf { it.leftMargin + it.rightMargin }
        val total = params.sumOf { it.weight.toDouble() }
        var cumulative = 0.0
        var previous = 0
        children.forEachIndexed { index, child ->
            cumulative += params[index].weight.toDouble()
            val edge = (available * cumulative / total).roundToInt()
            child.measure(View.MeasureSpec.makeMeasureSpec((edge - previous).coerceAtLeast(0), View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(child.measuredHeight, View.MeasureSpec.EXACTLY))
            previous = edge
        }
    }
}
