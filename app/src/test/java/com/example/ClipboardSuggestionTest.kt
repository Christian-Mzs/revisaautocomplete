package com.example

import android.content.ClipData
import android.content.ClipDescription
import android.content.ClipboardManager
import android.content.Context
import android.net.Uri
import android.os.PersistableBundle
import android.os.Bundle
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputContentInfo
import androidx.core.view.inputmethod.EditorInfoCompat
import androidx.test.core.app.ApplicationProvider
import com.example.clipboard.ClipboardHistory
import com.example.clipboard.ClipboardSuggestion
import com.example.clipboard.ClipboardSuggestionController
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class ClipboardSuggestionTest {
    private val context: Context get() = ApplicationProvider.getApplicationContext()
    private val clipboard get() = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    private val scope = TestScope()
    private val uri = Uri.parse("content://test/images/1")
    private var usable = true
    private var probes = 0
    private var changes = 0
    private val controller by lazy {
        ClipboardSuggestionController(context, scope, { changes++ },
            probeImage = { _, _ -> probes++; if (usable) "image/png" else null },
            loadPreview = { null })
    }
    private fun editor(vararg mime: String) = EditorInfo().apply {
        packageName = "test.editor"
        EditorInfoCompat.setContentMimeTypes(this, mime.toList().toTypedArray())
    }
    private fun image() = ClipData(ClipDescription("screenshot", arrayOf("image/png")),
        ClipData.Item("image label", null, null, uri))
    @Before fun clear() {
        clipboard.clearPrimaryClip()
        context.getSharedPreferences("clipboard_history", Context.MODE_PRIVATE).edit().clear().commit()
    }

    @Test fun `text appears pastes verbatim and disappears without clearing system clipboard`() {
        clipboard.setPrimaryClip(ClipData.newPlainText("", "  copied\ntext  "))
        val ic = FakeInputConnection("prefix")
        controller.updateEditor(ic, editor(), false)
        assertEquals("  copied\ntext  ", (controller.suggestion as ClipboardSuggestion.Text).text)
        assertTrue(controller.paste())
        assertEquals("prefix  copied\ntext  ", ic.currentText)
        assertNull(controller.suggestion)
        controller.updateEditor(ic, editor(), false)
        assertNull(controller.suggestion)
        assertEquals("  copied\ntext  ", clipboard.primaryClip!!.getItemAt(0).text.toString())
    }

    @Test fun `dismiss survives reopening focus and renders while history stays intact`() {
        clipboard.setPrimaryClip(ClipData.newPlainText("", "copied"))
        val history = ClipboardHistory(context).apply { capture() }
        controller.updateEditor(FakeInputConnection(), editor(), false)
        controller.dismiss()
        repeat(3) { controller.stop(); controller.updateEditor(FakeInputConnection(), editor(), false); controller.refresh() }
        assertNull(controller.suggestion)
        assertEquals("copied", clipboard.primaryClip!!.getItemAt(0).text.toString())
        assertEquals(listOf("copied"), history.entries())
        clipboard.setPrimaryClip(ClipData.newPlainText("", "new"))
        controller.clipboardChanged()
        assertEquals("new", (controller.suggestion as ClipboardSuggestion.Text).text)
    }

    @Test fun `image never persists even with incidental text and MIME decides eligibility`() {
        val history = ClipboardHistory(context)
        clipboard.setPrimaryClip(image())
        history.capture()
        assertTrue(history.entries().isEmpty())
        controller.updateEditor(FakeInputConnection(), editor(), false)
        scope.advanceUntilIdle()
        assertNull(controller.suggestion); assertEquals(0, probes)
        controller.updateEditor(FakeInputConnection(), editor("image/*"), false)
        scope.advanceUntilIdle()
        val item = controller.suggestion as ClipboardSuggestion.Image
        assertEquals(uri, item.uri); assertEquals("image/png", item.mime)
        assertNull(item.thumbnail)
        controller.updateEditor(FakeInputConnection(), editor("image/jpeg"), false)
        scope.advanceUntilIdle()
        assertNull(controller.suggestion)
        assertTrue(ClipboardHistory(context).entries().isEmpty())
    }

    @Test fun `accepted rich content carries URI MIME and temporary permission flag then closes`() {
        clipboard.setPrimaryClip(image())
        var received: InputContentInfo? = null
        var receivedFlags = 0
        val ic = object : FakeInputConnection() {
            override fun commitContent(inputContentInfo: InputContentInfo, flags: Int, opts: Bundle?): Boolean {
                received = inputContentInfo; receivedFlags = flags; return true
            }
        }
        controller.updateEditor(ic, editor("image/png"), false)
        scope.advanceUntilIdle()
        assertTrue(controller.paste())
        assertEquals(uri, received!!.contentUri)
        assertTrue(received!!.description.hasMimeType("image/png"))
        assertEquals(1, receivedFlags)
        assertNull(controller.suggestion)
        assertEquals("", ic.currentText)
        assertNotNull(clipboard.primaryClip)
    }

    @Test fun `rejected or throwing image commit preserves clipboard and revoked URI disappears`() {
        for (throws in listOf(false, true)) {
            clipboard.setPrimaryClip(image())
            controller.clipboardChanged()
            val ic = object : FakeInputConnection() {
                override fun commitContent(inputContentInfo: InputContentInfo, flags: Int, opts: Bundle?): Boolean {
                    if (throws) throw SecurityException("revoked")
                    return false
                }
            }
            controller.updateEditor(ic, editor("image/*"), false)
            scope.advanceUntilIdle()
            assertFalse(controller.paste())
            assertNotNull(clipboard.primaryClip)
            usable = false
            controller.refresh(); scope.advanceUntilIdle()
            assertNull(controller.suggestion)
            usable = true
        }
    }

    @Test fun `password fields and sensitive clips never show read probe persist or commit`() {
        for (clip in listOf(ClipData.newPlainText("", "secret"), image())) {
            clipboard.setPrimaryClip(clip)
            controller.updateEditor(FakeInputConnection(), editor("image/*"), true)
            scope.advanceUntilIdle()
            assertNull(controller.suggestion); assertFalse(controller.paste()); assertEquals(0, probes)
            clip.description.extras = PersistableBundle().apply {
                putBoolean("android.content.extra.IS_SENSITIVE", true)
            }
            clipboard.setPrimaryClip(clip)
            controller.updateEditor(FakeInputConnection(), editor("image/*"), false)
            controller.clipboardChanged(); scope.advanceUntilIdle()
            assertNull(controller.suggestion)
            assertEquals(0, probes)
            ClipboardHistory(context).apply { capture(); assertTrue(entries().isEmpty()) }
        }
    }

    @Test fun `image refresh rechecks revoked access without decoding on every refresh`() {
        clipboard.setPrimaryClip(image())
        controller.updateEditor(FakeInputConnection(), editor("image/*"), false)
        scope.advanceUntilIdle()
        assertNotNull(controller.suggestion)
        usable = false
        controller.updateEditor(FakeInputConnection(), editor("image/*"), false)
        scope.advanceUntilIdle()
        assertNull(controller.suggestion)
    }

    @Test fun `text history still records copied text and stale suggestion never pastes old item`() {
        clipboard.setPrimaryClip(ClipData.newPlainText("", "first"))
        val history = ClipboardHistory(context).apply { capture() }
        val ic = FakeInputConnection()
        controller.updateEditor(ic, editor(), false)
        clipboard.setPrimaryClip(ClipData.newPlainText("", "second"))
        assertFalse(controller.paste())
        assertEquals("", ic.currentText)
        history.capture()
        assertEquals(listOf("second", "first"), history.entries())
        assertTrue(controller.paste())
        assertEquals("second", ic.currentText)
    }
}
