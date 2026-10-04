package com.example

import com.example.suggestions.CurrentWordReplacement
import org.junit.Assert.*
import org.junit.Test

class CurrentWordReplacementTest {
    @Test fun `replaces only word around cursor and preserves surrounding text`() {
        val ic = FakeInputConnection("before wor|ld after".replace("|", ""), 10)
        assertTrue(CurrentWordReplacement.replace(ic, "world", "planet"))
        assertEquals("before planet after", ic.currentText)
    }

    @Test fun `does not replace if the current word no longer matches tapped suggestion`() {
        val ic = FakeInputConnection("hello other")
        assertFalse(CurrentWordReplacement.replace(ic, "world", "planet"))
        assertEquals("hello other", ic.currentText)
    }
}
