package com.example

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.PersistableBundle
import androidx.test.core.app.ApplicationProvider
import com.example.clipboard.ClipboardHistory
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ClipboardHistoryTest {
    private val context: Context get() = ApplicationProvider.getApplicationContext()
    @Before fun clear() {
        context.getSharedPreferences("clipboard_history", Context.MODE_PRIVATE).edit().clear().commit()
    }
    @Test fun historyIsBoundedDeduplicatedAndRestored() {
        val history = ClipboardHistory(context)
        repeat(35) { history.add("item $it") }
        assertEquals(30, history.entries().size)
        assertEquals("item 34", history.entries().first())
        assertFalse(history.entries().contains("item 4"))
        history.add("item 10")
        assertEquals("item 10", history.entries().first())
        assertEquals(30, history.entries().size)
        assertEquals(history.entries(), ClipboardHistory(context).entries())
        history.remove("item 10")
        assertFalse(ClipboardHistory(context).entries().contains("item 10"))
    }
    @Test fun captureSkipsSensitiveClipboardAndPreservesText() {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val history = ClipboardHistory(context)
        clipboard.setPrimaryClip(ClipData.newPlainText("", "  copied\ntext  "))
        history.capture()
        assertEquals(listOf("  copied\ntext  "), history.entries())
        val secret = ClipData.newPlainText("", "secret")
        secret.description.extras = PersistableBundle().apply {
            putBoolean("android.content.extra.IS_SENSITIVE", true)
        }
        clipboard.setPrimaryClip(secret)
        history.capture()
        assertEquals(1, history.entries().size)
    }
    @Test fun legacyStringsMigrateWithoutLosingWhitespaceOrOrder() {
        context.getSharedPreferences("clipboard_history", Context.MODE_PRIVATE).edit()
            .putString("items", "[\"  old\",\"second\"]").commit()
        val history = ClipboardHistory(context)
        assertEquals(listOf("  old", "second"), history.entries())
        assertTrue(history.records().none { it.pinned })
        history.setPinned(listOf("second"), true)
        val restored = ClipboardHistory(context)
        assertEquals(listOf("second", "  old"), restored.entries())
        assertTrue(restored.records().first().pinned)
    }
    @Test fun pinnedItemsAreStablePersistentAndOutsideTheNormalLimit() {
        val history = ClipboardHistory(context)
        history.add("one"); history.add("two")
        history.setPinned(listOf("one", "two"), true)
        repeat(40) { history.add("normal $it") }
        assertEquals(32, history.records().size)
        assertEquals(listOf("two", "one"), history.records().take(2).map { it.text })
        assertEquals(30, history.records().count { !it.pinned })
        history.add("one")
        assertEquals(listOf("two", "one"), history.entries().take(2))
        assertEquals(history.records(), ClipboardHistory(context).records())
        history.setPinned(listOf("two"), false)
        assertEquals("one", history.entries().first())
        assertEquals(30, history.records().count { !it.pinned })
        assertFalse(history.records().first { it.text == "two" }.pinned)
        history.removeAll(listOf("one", "two", "normal 39"))
        assertFalse(history.entries().any { it == "one" || it == "two" || it == "normal 39" })
    }
    @Test fun regularRecopyMovesToFrontWithoutDuplicating() {
        val history = ClipboardHistory(context)
        history.add("a"); history.add("b"); history.add("a")
        assertEquals(listOf("a", "b"), history.entries())
    }

}
