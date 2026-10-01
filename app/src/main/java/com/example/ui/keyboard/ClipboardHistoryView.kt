package com.example.ui.keyboard

import android.content.Context
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.text.TextUtils
import android.view.Gravity
import android.view.HapticFeedbackConstants
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import android.widget.CheckBox
import android.widget.FrameLayout
import android.widget.GridView
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import com.example.R
import com.example.clipboard.ClipboardEntry
import com.example.clipboard.ClipboardHistory
import com.example.clipboard.ClipboardManagementState

/** Text-only panel. Native grid gestures stay outside the central keyboard touch surface. */
internal class ClipboardHistoryView(
    context: Context,
    private val history: ClipboardHistory,
    private val sensitive: () -> Boolean,
    private val paste: (String) -> Unit,
    private val back: () -> Unit
) : LinearLayout(context) {
    internal val management = ClipboardManagementState()
    private var entries = emptyList<ClipboardEntry>()
    private val header = LinearLayout(context).apply { gravity = Gravity.CENTER_VERTICAL }
    private val grid = GridView(context).apply {
        numColumns = 3
        horizontalSpacing = dp(6)
        verticalSpacing = dp(6)
        stretchMode = GridView.STRETCH_COLUMN_WIDTH
        setPadding(dp(4), dp(6), dp(4), dp(6))
        clipToPadding = false
    }
    private val empty = TextView(context).apply {
        text = "Os textos copiados aparecerão aqui. Até 30 itens normais; os fixados permanecem."
        setTextColor(Color.LTGRAY)
        textSize = 14f
        setPadding(dp(12), dp(16), dp(12), dp(16))
        gravity = Gravity.CENTER
    }
    private val adapter = object : BaseAdapter() {
        override fun getCount() = entries.size
        override fun getItem(position: Int) = entries[position]
        override fun getItemId(position: Int) = position.toLong()
        override fun getView(position: Int, convertView: View?, parent: ViewGroup): View = card(entries[position])
    }

    init {
        orientation = VERTICAL
        addView(header, LayoutParams(LayoutParams.MATCH_PARENT, dp(KeyboardGeometry.NUMBER_KEY_HEIGHT_DP)))
        val body = FrameLayout(context)
        body.addView(grid, FrameLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
        body.addView(empty, FrameLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
        addView(body, LayoutParams(LayoutParams.MATCH_PARENT, 0, 1f))
        grid.adapter = adapter
        grid.addOnLayoutChangeListener { _, l, _, r, _, _, _, _, _ ->
            val columns = if (r - l >= dp(300)) 3 else 2
            if (grid.numColumns != columns) grid.numColumns = columns
        }
        refresh()
    }

    fun refresh() {
        entries = if (sensitive()) emptyList() else history.records()
        if (sensitive()) management.end() else management.reconcile(entries)
        renderHeader()
        empty.visibility = if (entries.isEmpty()) View.VISIBLE else View.GONE
        grid.visibility = if (entries.isEmpty()) View.GONE else View.VISIBLE
        // GONE grids do not lay out adapter changes; detach to erase old sensitive cards.
        if (entries.isEmpty()) grid.adapter = null
        else if (grid.adapter == null) grid.adapter = adapter
        adapter.notifyDataSetChanged()
    }

    private fun icon(resource: Int, description: String, enabled: Boolean = true, action: () -> Unit) =
        ImageView(context).apply {
            contentDescription = description
            setImageResource(resource)
            setColorFilter(Color.WHITE)
            scaleType = ImageView.ScaleType.CENTER_INSIDE
            setPadding(dp(10), dp(8), dp(10), dp(8))
            layoutParams = LayoutParams(dp(40), LayoutParams.MATCH_PARENT)
            isEnabled = enabled
            alpha = if (enabled) 1f else 0.35f
            setOnClickListener { if (!sensitive()) action() }
        }

    private fun renderHeader() {
        header.removeAllViews()
        header.addView(icon(R.drawable.ic_clipboard_back, "Voltar ao teclado") {
            if (management.active) { management.end(); refresh() } else back()
        }.apply {
            // Back is always available, even if the editor has become sensitive.
            setOnClickListener { if (management.active) { management.end(); refresh() } else back() }
            contentDescription = if (management.active) "Sair do gerenciamento" else "Voltar ao teclado"
        })
        if (!management.active) {
            header.addView(TextView(context).apply {
                text = "Área de transferência"
                textSize = 16f; setTextColor(Color.WHITE); gravity = Gravity.CENTER_VERTICAL
                layoutParams = LayoutParams(0, LayoutParams.MATCH_PARENT, 1f)
            })
            return
        }
        header.addView(CheckBox(context).apply {
            text = "Todos"
            contentDescription = "Selecionar ou desmarcar todos"
            setTextColor(Color.WHITE)
            isChecked = entries.isNotEmpty() && management.count == entries.size
            layoutParams = LayoutParams(0, LayoutParams.MATCH_PARENT, 1f)
            setOnClickListener { if (!sensitive()) { management.toggleAll(entries); refresh() } }
        })
        header.addView(TextView(context).apply {
            text = management.count.toString()
            contentDescription = "${management.count} itens selecionados"
            setTextColor(Color.WHITE); gravity = Gravity.CENTER
            layoutParams = LayoutParams(dp(28), LayoutParams.MATCH_PARENT)
        })
        val chosen = management.selectedTexts()
        val unpin = chosen.isNotEmpty() && entries.filter { it.text in chosen }.all { it.pinned }
        header.addView(icon(R.drawable.ic_clipboard_pin, if (unpin) "Desafixar selecionados" else "Fixar selecionados", chosen.isNotEmpty()) {
            history.setPinned(chosen, !unpin)
            management.end(); refresh()
        })
        header.addView(icon(R.drawable.ic_clipboard_delete, "Excluir selecionados", chosen.isNotEmpty()) {
            history.removeAll(chosen)
            management.end(); refresh()
        })
    }

    private fun card(entry: ClipboardEntry): View {
        val selected = entry.text in management.selectedTexts()
        val frame = FrameLayout(context).apply {
            tag = "clipboard_card"
            layoutParams = android.widget.AbsListView.LayoutParams(LayoutParams.MATCH_PARENT, dp(90))
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#262933")); cornerRadius = dp(8).toFloat()
                if (selected) setStroke(dp(2), Color.parseColor("#60A5FA"))
            }
            contentDescription = "${entry.text}; ${if (entry.pinned) "fixado" else "não fixado"}; ${if (selected) "selecionado" else "não selecionado"}"
            isSelected = selected
            setOnClickListener {
                if (sensitive()) return@setOnClickListener
                if (management.active) { management.toggle(entry.text); refresh() } else paste(entry.text)
            }
            setOnLongClickListener {
                if (sensitive()) return@setOnLongClickListener false
                performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
                if (!management.active) management.begin(entry.text) else management.toggle(entry.text)
                refresh(); true
            }
        }
        frame.addView(TextView(context).apply {
            text = entry.text
            textSize = 13f; setTextColor(Color.WHITE)
            gravity = Gravity.TOP or Gravity.START
            maxLines = 4; ellipsize = TextUtils.TruncateAt.END
            setPadding(dp(8), dp(if (management.active || entry.pinned) 24 else 8), dp(8), dp(6))
            layoutParams = FrameLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT)
            setOnClickListener { frame.performClick() }
            setOnLongClickListener { frame.performLongClick() }
        })
        if (management.active || entry.pinned) frame.addView(ImageView(context).apply {
            setImageResource(if (management.active) {
                if (selected) R.drawable.ic_clipboard_checked else R.drawable.ic_clipboard_unchecked
            } else R.drawable.ic_clipboard_pin)
            setColorFilter(Color.WHITE)
            contentDescription = if (management.active) {
                if (selected) "Selecionado" else "Não selecionado"
            } else "Item fixado"
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
            layoutParams = FrameLayout.LayoutParams(dp(18), dp(18), Gravity.TOP or Gravity.END).apply {
                topMargin = dp(4); marginEnd = dp(5)
            }
        })
        return frame
    }

    private fun dp(value: Int) = (value * resources.displayMetrics.density + 0.5f).toInt()
}
