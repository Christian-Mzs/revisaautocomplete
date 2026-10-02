package com.example.codex

import org.junit.Assert.*
import org.junit.Test

class RuntimeReadinessTest {
    @Test fun `only matching validated version and intact files are ready`() {
        assertTrue(RuntimeReadiness.matches("v1","v1",true))
        assertFalse(RuntimeReadiness.matches("v2","v1",true))
        assertFalse(RuntimeReadiness.matches("v1",null,true))
        assertFalse(RuntimeReadiness.matches("v1","v1",false))
        assertFalse(RuntimeReadiness.matches("","",true))
    }
}
