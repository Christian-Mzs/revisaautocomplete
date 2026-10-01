package com.example.ime

/** Release builds contain no diagnostic state or logging. */
@Suppress("NOTHING_TO_INLINE", "UNUSED_PARAMETER")
internal object InputMetrics {
    inline fun reset() = Unit
    inline fun finishSession() = Unit
    inline fun event(action: Int, pointers: Int) = Unit
    inline fun resolved() = Unit
    inline fun miss() = Unit
    inline fun activation() = Unit
    inline fun abandoned() = Unit
    inline fun character() = Unit
    inline fun direct() = Unit
    inline fun commit() = Unit
    inline fun commitResult(ok: Boolean) = Unit
    inline fun delete() = Unit
    inline fun editorAction() = Unit
}
