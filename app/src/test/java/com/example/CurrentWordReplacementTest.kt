package com.example

import com.example.suggestions.CurrentWordReplacement
import org.junit.Assert.*
import org.junit.Test

class CurrentWordReplacementTest {
    @Test fun `replaces only word around cursor and preserves surrounding text`() {
        val ic = FakeInputConnection("before world after", 12)
        assertTrue(CurrentWordReplacement.replace(ic, "world", "planet"))
        assertEquals("before planet after", ic.currentText)
    }

    @Test fun `empty tracked word is rejected`() {
        val ic = FakeInputConnection("hello other")
        assertFalse(CurrentWordReplacement.replace(ic, "", "planet"))
        assertEquals("hello other", ic.currentText)
    }
}

