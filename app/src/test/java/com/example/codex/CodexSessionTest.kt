package com.example.codex

import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

class CodexSessionTest {
    @Test fun `logout executes official command then confirms disconnected status`() = runTest {
        val calls = mutableListOf<List<String>>()
        val session = CodexSession { args ->
            calls.add(args)
            if (args == listOf("logout")) CliOutput(0,"","Successfully logged out")
            else CliOutput(1,"","Not logged in\n")
        }
        val state = session.logout()
        assertEquals(listOf(listOf("logout"),listOf("login","status")),calls)
        assertFalse(state.connected)
        assertEquals("Não conectado",state.label)
    }
    @Test fun `failed logout throws without reporting disconnected`() = runTest {
        val calls = mutableListOf<List<String>>()
        val session = CodexSession { args -> calls.add(args); CliOutput(1,"","logout failed") }
        try { session.logout(); fail("Expected logout failure") }
        catch (e: IllegalStateException) { assertTrue(e.message!!.contains("Logout falhou")) }
        assertEquals(listOf(listOf("logout")),calls)
    }
    @Test fun `still connected after logout is a failure`() = runTest {
        val session = CodexSession { CliOutput(0,"","Logged in using ChatGPT") }
        try { session.logout(); fail("Expected verification failure") }
        catch (e: IllegalStateException) { assertTrue(e.message!!.contains("ainda informa")) }
    }
    @Test fun `status error after successful logout cannot be treated as disconnected`() = runTest {
        val session = CodexSession { args ->
            if (args == listOf("logout")) CliOutput(0,"","")
            else CliOutput(1,"","Error loading configuration")
        }
        try { session.logout(); fail("Expected status failure") }
        catch (e: IllegalStateException) { assertTrue(e.message!!.contains("verificar a sessão")) }
    }
    @Test fun `saved authenticated session is recognized without any login flags`() = runTest {
        val session = CodexSession { args ->
            assertEquals(listOf("login","status"),args)
            CliOutput(0,"","Logged in using ChatGPT")
        }
        assertTrue(session.status().connected)
    }
}
