package com.example

import com.example.ui.keyboard.EmojiCatalog
import org.junit.Assert.*
import org.junit.Test

class EmojiCatalogTest {
    @Test fun `catalog covers Unicode 16 without duplicate sequences`() {
        val displayed = EmojiCatalog.categories.flatMap { it.emojis }
        val all = (displayed + EmojiCatalog.variants.values.flatten()).distinct()
        assertEquals(9, EmojiCatalog.categories.size)
        assertEquals(displayed.size, displayed.distinct().size)
        assertEquals(3781, all.size)
        assertTrue(all.containsAll(listOf("🫩", "🥹", "❤️", "🇧🇷", "👨‍👩‍👧‍👦", "👍🏽")))
    }

    @Test fun `simple skin tones stay grouped with their base emoji`() {
        assertEquals(listOf("👍", "👍🏻", "👍🏼", "👍🏽", "👍🏾", "👍🏿"), EmojiCatalog.variants["👍"])
        assertTrue(EmojiCatalog.variants.values.all { it.size == 6 })
        assertFalse(EmojiCatalog.categories.flatMap { it.emojis }.contains("👍🏽"))
    }
}
