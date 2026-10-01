package com.example

import android.content.Context
import android.view.View
import android.view.ViewGroup
import androidx.test.core.app.ApplicationProvider
import com.example.clipboard.ClipboardHistory
import com.example.clipboard.ClipboardManagementState
import com.example.clipboard.ClipboardEntry
import com.example.ui.keyboard.ClipboardHistoryView
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ClipboardManagementTest {
    private val context: Context get() = ApplicationProvider.getApplicationContext()
    @Before fun clear() { context.getSharedPreferences("clipboard_history", Context.MODE_PRIVATE).edit().clear().commit() }
    private fun descendants(view: View): List<View> = listOf(view) + if (view is ViewGroup)
        (0 until view.childCount).flatMap { descendants(view.getChildAt(it)) } else emptyList()
    private fun layout(view: View) {
        view.measure(View.MeasureSpec.makeMeasureSpec(360, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(275, View.MeasureSpec.EXACTLY))
        view.layout(0, 0, 360, 275)
    }
    private fun cards(panel: View) = descendants(panel).filter { it.tag == "clipboard_card" }
    private fun click(panel: View, label: String) {
        descendants(panel).first { it.contentDescription == label }.performClick(); layout(panel)
    }
    @Test fun selectAllToggleAndResetAreDeterministic() {
        val state = ClipboardManagementState()
        val entries = listOf(ClipboardEntry("a"), ClipboardEntry("b"))
        state.begin("a"); assertEquals(setOf("a"), state.selectedTexts())
        state.toggleAll(entries); assertEquals(2, state.count)
        state.toggleAll(entries); assertEquals(0, state.count)
        state.toggle("b"); assertEquals(1, state.count)
        state.end(); assertFalse(state.active); assertEquals(0, state.count)
    }
    @Test fun normalPasteLongPressSelectionPinUnpinDeleteAndBack() {
        val history = ClipboardHistory(context).apply { add("a"); add("b"); add("c") }
        val pasted = mutableListOf<String>()
        var closed = false
        val panel = ClipboardHistoryView(context, history, { false }, { pasted.add(it) }, { closed = true })
        layout(panel)
        cards(panel).first().performClick()
        assertEquals(listOf("c"), pasted)
        cards(panel).first().performLongClick(); layout(panel)
        assertTrue(panel.management.active); assertEquals(1, panel.management.count)
        cards(panel)[1].performClick(); layout(panel)
        assertEquals(2, panel.management.count); assertEquals(1, pasted.size)
        assertTrue(descendants(panel).any { it.contentDescription == "2 itens selecionados" })
        click(panel, "Fixar selecionados")
        assertFalse(panel.management.active)
        assertEquals(setOf("b", "c"), history.records().filter { it.pinned }.map { it.text }.toSet())
        cards(panel).first().performLongClick(); layout(panel)
        click(panel, "Desafixar selecionados")
        assertEquals(1, history.records().count { it.pinned })
        cards(panel).first().performLongClick(); layout(panel)
        click(panel, "Selecionar ou desmarcar todos")
        assertEquals(3, panel.management.count)
        click(panel, "Selecionar ou desmarcar todos")
        assertEquals(0, panel.management.count)
        click(panel, "Sair do gerenciamento")
        assertFalse(panel.management.active); assertEquals(0, panel.management.count)
        cards(panel).first().performLongClick(); layout(panel)
        click(panel, "Selecionar ou desmarcar todos")
        click(panel, "Excluir selecionados")
        assertTrue(history.entries().isEmpty()); assertFalse(panel.management.active)
        click(panel, "Voltar ao teclado"); assertTrue(closed)
    }
    @Test fun sensitiveStateHidesEntriesAndBlocksPreviouslyRenderedCards() {
        val history = ClipboardHistory(context).apply { add("private") }
        var sensitive = false
        var pasted = false
        val panel = ClipboardHistoryView(context, history, { sensitive }, { pasted = true }, {})
        layout(panel)
        val old = cards(panel).first()
        sensitive = true
        old.performClick(); old.performLongClick()
        assertFalse(pasted); assertFalse(panel.management.active)
        panel.refresh(); layout(panel)
        assertTrue(cards(panel).isEmpty())
        assertEquals(listOf("private"), history.entries())
    }
}
