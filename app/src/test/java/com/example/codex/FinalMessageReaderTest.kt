package com.example.codex

import java.nio.file.Files
import java.io.File
import org.junit.Assert.*
import org.junit.Test

class FinalMessageReaderTest {
    @Test fun `only the final message file is read`() {
        val dir = Files.createTempDirectory("revisa-result").toFile()
        try {
            File(dir,"stdout.txt").writeText("diagnostics")
            File(dir,"result.txt").writeText("  texto final\n")
            assertEquals("texto final",FinalMessageReader.read(dir))
            File(dir,"result.txt").delete()
            try { FinalMessageReader.read(dir); fail("Missing final file must fail") }
            catch (_: IllegalStateException) {}
            File(dir,"result.txt").writeText(" \n")
            try { FinalMessageReader.read(dir); fail("Blank final file must fail") }
            catch (_: IllegalStateException) {}
        } finally { dir.deleteRecursively() }
    }
}
