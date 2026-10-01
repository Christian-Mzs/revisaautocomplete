package com.example

import android.text.InputType
import android.view.inputmethod.EditorInfo
import com.example.ime.CurrentWord
import com.example.ime.CurrentWordExtractor
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class CurrentWordExtractorTest {
    private fun editor() = EditorInfo().apply { inputType = InputType.TYPE_CLASS_TEXT }

    @Test fun `extracts only word around cursor`() {
        assertEquals(CurrentWord("vo", "ce"), CurrentWordExtractor.extract(
            FakeInputConnection("oi, voce! tudo bem", 6), editor()))
        assertEquals(CurrentWord("não", ""), CurrentWordExtractor.extract(
            FakeInputConnection("bom dia\nnão"), editor()))
    }

    @Test fun `delimiters selection and missing context produce no word`() {
        for (text in listOf("", "nao ", "nao!", "nao\n")) {
            assertNull(CurrentWordExtractor.extract(FakeInputConnection(text), editor()))
        }
        assertNull(CurrentWordExtractor.extract(FakeInputConnection("voce", 0, 4), editor()))
        assertNull(CurrentWordExtractor.extract(FakeInputConnection("nao"), null))
        assertNull(CurrentWordExtractor.extract(null, editor()))
    }

    @Test fun `reads bounded context and refuses truncated words`() {
        val ic = object : FakeInputConnection("x".repeat(300) + " nao") {
            override fun getTextBeforeCursor(n: Int, flags: Int): CharSequence? {
                assertEquals(CurrentWordExtractor.CONTEXT_LIMIT, n)
                return super.getTextBeforeCursor(n, flags)
            }
            override fun getTextAfterCursor(n: Int, flags: Int): CharSequence? {
                assertEquals(CurrentWordExtractor.CONTEXT_LIMIT, n)
                return super.getTextAfterCursor(n, flags)
            }
        }
        assertEquals("nao", CurrentWordExtractor.extract(ic, editor())?.text)
        assertNull(CurrentWordExtractor.extract(FakeInputConnection("a".repeat(80)), editor()))
        assertNull(CurrentWordExtractor.extract(FakeInputConnection("a".repeat(80), 1), editor()))
    }

    @Test fun `every Android password variation blocks every suggestion read`() {
        val types = listOf(
            InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD,
            InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_WEB_PASSWORD,
            InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD,
            InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_VARIATION_PASSWORD
        )
        val ic = object : FakeInputConnection("secret") {
            override fun getSelectedText(flags: Int): CharSequence? = error("Password selection read")
            override fun getTextBeforeCursor(n: Int, flags: Int): CharSequence? = error("Password prefix read")
            override fun getTextAfterCursor(n: Int, flags: Int): CharSequence? = error("Password suffix read")
        }
        for (type in types) {
            assertNull(CurrentWordExtractor.extract(ic, EditorInfo().apply { inputType = type }))
        }
    }
}
