package com.example.clipboard

/** Ephemeral selection, scoped to one visit of the clipboard panel. */
class ClipboardManagementState {
    var active = false
        private set
    private val selected = linkedSetOf<String>()
    fun selectedTexts(): Set<String> = selected.toSet()
    val count get() = selected.size
    fun begin(text: String) { active = true; selected.clear(); selected.add(text) }
    fun toggle(text: String) { if (active && !selected.add(text)) selected.remove(text) }
    fun toggleAll(entries: List<ClipboardEntry>) {
        val all = entries.map { it.text }.toSet()
        if (selected.containsAll(all)) selected.clear() else { selected.clear(); selected.addAll(all) }
    }
    fun reconcile(entries: List<ClipboardEntry>) { selected.retainAll(entries.map { it.text }.toSet()) }
    fun end() { active = false; selected.clear() }
}
