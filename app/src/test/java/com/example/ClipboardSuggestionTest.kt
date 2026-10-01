package com.example

import android.content.ClipData
import android.content.ClipDescription
import android.content.ClipboardManager
import android.content.Context
import android.net.Uri
import android.os.PersistableBundle
import androidx.test.core.app.ApplicationProvider
import com.example.clipboard.ClipboardHistory
import com.example.clipboard.ClipboardSuggestionController
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ClipboardSuggestionTest {
    private val context: Context get() = ApplicationProvider.getApplicationContext()
    private val clipboard get() = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    private val controller by lazy { ClipboardSuggestionController(context) {} }
    @Before fun clear() {
        clipboard.clearPrimaryClip()
        context.getSharedPreferences("clipboard_history", Context.MODE_PRIVATE).edit().clear().commit()
    }
    @Test fun `text appears pastes verbatim and stays dismissed after focus and reopening`() {
        clipboard.setPrimaryClip(ClipData.newPlainText("", "  copied\ntext  "))
        val ic = FakeInputConnection("prefix")
        controller.updateEditor(ic, false)
        assertEquals("  copied\ntext  ", controller.suggestion!!.text)
        assertTrue(controller.paste())
        assertEquals("prefix  copied\ntext  ", ic.currentText)
        controller.stop(); controller.updateEditor(ic, false); controller.refresh()
        assertNull(controller.suggestion)
        assertEquals("  copied\ntext  ", clipboard.primaryClip!!.getItemAt(0).text.toString())
    }
    @Test fun `dismiss preserves history and clipboard until a new copy`() {
        clipboard.setPrimaryClip(ClipData.newPlainText("", "copied"))
        val history = ClipboardHistory(context).apply { capture() }
        controller.updateEditor(FakeInputConnection(), false)
        controller.dismiss()
        repeat(3) { controller.stop(); controller.updateEditor(FakeInputConnection(), false) }
        assertNull(controller.suggestion)
        assertEquals(listOf("copied"), history.entries())
        assertEquals("copied", clipboard.primaryClip!!.getItemAt(0).text.toString())
        clipboard.setPrimaryClip(ClipData.newPlainText("", "new")); controller.clipboardChanged()
        assertEquals("new", controller.suggestion!!.text)
    }
    @Test fun `URI media and image labels never become text suggestions or history`() {
        for (mime in listOf("image/png", "text/plain")) {
            val clip = ClipData(ClipDescription("", arrayOf(mime)),
                ClipData.Item("incidental label", null, null, Uri.parse("content://test/1")))
            clipboard.setPrimaryClip(clip)
            controller.updateEditor(FakeInputConnection(), false)
            assertNull(controller.suggestion)
            ClipboardHistory(context).apply { capture(); assertTrue(entries().isEmpty()) }
        }
        clipboard.setPrimaryClip(ClipData(ClipDescription("", arrayOf("image/png")), ClipData.Item("label")))
        controller.clipboardChanged()
        assertNull(controller.suggestion)
    }
    @Test fun `sensitive fields and sensitive clipboard are blocked`() {
        val clip = ClipData.newPlainText("", "secret")
        clipboard.setPrimaryClip(clip)
        controller.updateEditor(FakeInputConnection(), true)
        assertNull(controller.suggestion); assertFalse(controller.paste())
        clip.description.extras = PersistableBundle().apply { putBoolean("android.content.extra.IS_SENSITIVE", true) }
        clipboard.setPrimaryClip(clip)
        controller.updateEditor(FakeInputConnection(), false)
        assertNull(controller.suggestion)
        ClipboardHistory(context).apply { capture(); assertTrue(entries().isEmpty()) }
    }
    @Test fun `stale clipboard and rejected commit never paste or dismiss incorrectly`() {
        clipboard.setPrimaryClip(ClipData.newPlainText("", "first"))
        val ic = FakeInputConnection()
        controller.updateEditor(ic, false)
        clipboard.setPrimaryClip(ClipData.newPlainText("", "second"))
        assertFalse(controller.paste()); assertEquals("", ic.currentText)
        assertTrue(controller.paste()); assertEquals("second", ic.currentText)
        controller.clipboardChanged()
        controller.updateEditor(object : FakeInputConnection() {
            override fun commitText(text: CharSequence?, newCursorPosition: Int) = false
        }, false)
        assertFalse(controller.paste()); assertNotNull(controller.suggestion)
    }
}
