package com.example

import android.content.ClipData
import android.content.ClipDescription
import android.content.ClipboardManager
import android.content.Context
import android.net.Uri
import android.os.PersistableBundle
import android.view.inputmethod.EditorInfo
import androidx.core.view.inputmethod.EditorInfoCompat
import androidx.test.core.app.ApplicationProvider
import com.example.clipboard.ClipboardSuggestionController
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowLog

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class ClipboardDiagnosticTest {
    private val context: Context get() = ApplicationProvider.getApplicationContext()
    private val clipboard get() = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    private fun editor(vararg types: String) = EditorInfo().apply {
        packageName = "test.editor"
        EditorInfoCompat.setContentMimeTypes(this, types.toList().toTypedArray())
    }
    private fun clip(scheme: String = "content") = ClipData(ClipDescription("private label", arrayOf("image/png")),
        ClipData.Item("private copied text", null, null, Uri.parse("$scheme://private.authority/private-path")))
    private fun logs() = ShadowLog.getLogsForTag(ClipboardSuggestionController.DIAGNOSTIC_TAG).joinToString { it.msg }

    @Test fun `non image and absent clipboard always have visible safe summaries`() {
        val c = ClipboardSuggestionController(context, TestScope(), {})
        clipboard.clearPrimaryClip()
        c.updateEditor(FakeInputConnection(), editor(), false)
        assertTrue(c.diagnosticSummary!!.contains("exists=N"))
        assertTrue(c.diagnosticSummary!!.contains("reason=NO_PRIMARY_CLIP"))
        clipboard.setPrimaryClip(ClipData.newPlainText("secret label", "secret text"))
        c.clipboardChanged()
        assertTrue(c.diagnosticSummary!!.contains("reason=CLIPBOARD_NOT_IMAGE"))
        assertTrue(c.diagnosticSummary!!.contains("text=Y uri=N scheme=null"))
        assertTrue(c.diagnosticSummary!!.contains("imageCandidate=N"))
        assertFalse(c.diagnosticSummary!!.contains("secret"))
        // Unknown representation: no URI and no image MIME must also remain visible.
        clipboard.setPrimaryClip(ClipData(ClipDescription("", arrayOf("application/octet-stream")),
            ClipData.Item(android.content.Intent("private.action"))))
        c.clipboardChanged()
        assertTrue(c.diagnosticSummary!!.contains("text=N uri=N scheme=null"))
        assertTrue(c.diagnosticSummary!!.contains("mime=[application/octet-stream]"))
        assertTrue(c.diagnosticSummary!!.contains("reason=CLIPBOARD_NOT_IMAGE"))
        c.refresh()
        assertNotNull(c.diagnosticSummary)
        c.updateEditor(FakeInputConnection(), editor(), true)
        assertNull(c.diagnosticSummary)
    }

    @Test fun `editor MIME rejection is explicit without probing URI or leaking content`() {
        val scope = TestScope()
        val c = ClipboardSuggestionController(context, scope, {},
            probeImage = { _, _ -> error("Must not open when editor unsupported") }, loadPreview = { error("Must not decode") })
        clipboard.setPrimaryClip(clip())
        c.updateEditor(FakeInputConnection(), editor(), false)
        scope.advanceUntilIdle()
        assertNull(c.suggestion)
        assertTrue(c.diagnosticSummary!!.contains("IMAGE_SKIP_EDITOR_NO_IMAGE_MIME"))
        assertTrue(c.diagnosticSummary!!.contains("editorMime=[]"))
        assertTrue(logs().contains("OPEN=NOT_ATTEMPTED"))
        for (secret in listOf("private copied text", "private.authority", "private-path", "private label")) {
            assertFalse(logs().contains(secret)); assertFalse(c.diagnosticSummary!!.contains(secret))
        }
    }

    @Test fun `missing and non content URI exits leave distinct reasons`() {
        val c = ClipboardSuggestionController(context, TestScope(), {})
        clipboard.setPrimaryClip(ClipData(ClipDescription("", arrayOf("image/png")), ClipData.Item("not logged")))
        c.updateEditor(FakeInputConnection(), editor("image/*"), false)
        assertTrue(c.diagnosticSummary!!.contains("IMAGE_SKIP_NO_URI"))
        clipboard.setPrimaryClip(clip("file")); c.clipboardChanged()
        assertTrue(c.diagnosticSummary!!.contains("IMAGE_SKIP_NON_CONTENT_URI"))
    }

    @Test fun `sensitive field and marked clipboard expose no metadata or visible diagnostics`() {
        val c = ClipboardSuggestionController(context, TestScope(), {}, probeImage = { _, _ -> error("Sensitive read") })
        clipboard.setPrimaryClip(clip())
        ShadowLog.clear()
        c.updateEditor(FakeInputConnection(), editor("image/*"), true)
        assertNull(c.diagnosticSummary)
        assertEquals("IMAGE_SKIP_SENSITIVE", logs())
        val secret = clip().apply {
            description.extras = PersistableBundle().apply { putBoolean("android.content.extra.IS_SENSITIVE", true) }
        }
        clipboard.setPrimaryClip(secret)
        ShadowLog.clear()
        c.updateEditor(FakeInputConnection(), editor("image/*"), false)
        assertNull(c.diagnosticSummary)
        assertTrue(logs().contains("IMAGE_SKIP_SENSITIVE_CLIP"))
        assertFalse(logs().contains("CLIPBOARD primaryClip"))
    }

    @Test fun `MIME mismatch and failed thumbnail report exact terminal stages`() {
        val scope = TestScope()
        val c = ClipboardSuggestionController(context, scope, {}, probeImage = { _, _ -> "image/png" }, loadPreview = { null })
        clipboard.setPrimaryClip(clip())
        c.updateEditor(FakeInputConnection(), editor("image/jpeg"), false)
        scope.advanceUntilIdle()
        assertTrue(c.diagnosticSummary!!.contains("IMAGE_SKIP_MIME_MISMATCH"))
        assertTrue(c.diagnosticSummary!!.contains("supports=false"))
        c.updateEditor(FakeInputConnection(), editor("image/*"), false)
        scope.advanceUntilIdle()
        assertNotNull(c.suggestion)
        assertTrue(c.diagnosticSummary!!.contains("IMAGE_THUMBNAIL_FAILED"))
        assertTrue(c.diagnosticSummary!!.contains("supports=true"))
    }
}
