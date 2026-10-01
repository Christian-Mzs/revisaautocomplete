package com.example

import com.example.ime.GraphemeBackspace
import org.junit.Assert.assertEquals
import org.junit.Test

class GraphemeBackspaceTest {
    @Test fun `backspace removes a whole visible emoji or combining character`() {
        for (last in listOf("😀", "❤️", "🇧🇷", "👍🏽", "👨‍👩‍👧‍👦", "a\u0301")) {
            assertEquals(last.length, GraphemeBackspace.deletionLength("texto $last"))
        }
        assertEquals(1, GraphemeBackspace.deletionLength("texto a"))
        assertEquals(0, GraphemeBackspace.deletionLength(""))
    }
}
