package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.ime.LocalSuggestionEngine
import com.example.ime.OfflineDictionary
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.ByteArrayInputStream
import android.text.InputType
import android.view.inputmethod.EditorInfo
import com.example.ime.KeyboardController
import com.example.translation.TranslationService
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
@OptIn(ExperimentalCoroutinesApi::class)
class OfflineDictionaryTest {
    companion object {
        private fun openDictionary(): java.io.InputStream =
            OfflineDictionaryTest::class.java.classLoader
                ?.getResourceAsStream(OfflineDictionary.ASSET_PATH)
                ?: error("Missing test resource: ${OfflineDictionary.ASSET_PATH}. Check the test resources source set.")

        private val dictionary: OfflineDictionary by lazy {
            OfflineDictionary.read(openDictionary())
        }
    }

    @Test fun `packaged dictionary provides broad vocabulary and conjugated forms`() {
        assertTrue(dictionary.wordCount > 300_000)
        assertTrue("teste" in dictionary.suggest("teste"))
        assertTrue("testando" in dictionary.suggest("teste"))
        assertTrue("testando" in dictionary.suggest("testa"))
        assertTrue("testando" in dictionary.suggest("testan"))
        assertTrue("computador" in dictionary.suggest("computador"))
        assertTrue("avião" in dictionary.suggest("aviao"))
    }

    @Test fun `queries are bounded unique and do not invent personalized words`() {
        for (word in listOf("test", "testa", "teste", "comput", "casas", "aviao")) {
            val candidates = dictionary.suggest(word)
            assertTrue(candidates.size <= 3)
            assertEquals(candidates.distinct(), candidates)
        }
        assertTrue(dictionary.suggest("testandoooo").isEmpty())
        assertTrue(dictionary.suggest("xyzzyxyzzy").isEmpty())
        assertTrue(dictionary.suggest("t").isEmpty())
        assertTrue(dictionary.suggest("word123").isEmpty())
    }

    @Test fun `facade retains accents and capitalization after asset loads`() {
        LocalSuggestionEngine.loadDictionary { openDictionary() }
        assertEquals(listOf("não", "nao"), LocalSuggestionEngine.suggest("nao"))
        assertEquals("TESTE", LocalSuggestionEngine.suggest("TESTE").first())
        assertTrue("Testando" in LocalSuggestionEngine.suggest("Teste"))
        assertTrue("você" in LocalSuggestionEngine.suggest("vo"))
    }

    @Test fun `corrupt assets are rejected`() {
        try {
            OfflineDictionary.read(ByteArrayInputStream(byteArrayOf(1, 2, 3)))
            fail("Invalid data must be rejected")
        } catch (_: java.io.IOException) {
            // A corrupt gzip stream is rejected before index allocation.
        }
    }

    @Test fun `dictionary suggestion tap replaces only current word without provider calls`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        LocalSuggestionEngine.loadDictionary { openDictionary() }
        val scope = TestScope()
        val provider = FakeTranslationProvider()
        val controller = KeyboardController(context, scope, TranslationService(provider = provider),
            onStateChanged = {}, suggestionDispatcher = StandardTestDispatcher(scope.testScheduler))
        val ic = FakeInputConnection("um teste aqui", 8)
        controller.updateInputConnection(ic, EditorInfo().apply { inputType = InputType.TYPE_CLASS_TEXT })
        scope.testScheduler.advanceUntilIdle()
        assertTrue("testando" in controller.suggestions)
        assertTrue(controller.applySuggestion("testando"))
        assertEquals("um testando aqui", ic.currentText)
        scope.testScheduler.advanceUntilIdle()
        assertEquals(0, provider.callCount)
    }

    @Test fun `pending query cannot republish candidates after switching to password field`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        LocalSuggestionEngine.loadDictionary { openDictionary() }
        val scope = TestScope()
        val controller = KeyboardController(context, scope, onStateChanged = {},
            suggestionDispatcher = StandardTestDispatcher(scope.testScheduler))
        controller.updateInputConnection(FakeInputConnection("teste"), EditorInfo().apply {
            inputType = InputType.TYPE_CLASS_TEXT
        })
        val password = object : FakeInputConnection("secret") {
            override fun getSelectedText(flags: Int): CharSequence? = error("Password read")
            override fun getTextBeforeCursor(n: Int, flags: Int): CharSequence? = error("Password read")
            override fun getTextAfterCursor(n: Int, flags: Int): CharSequence? = error("Password read")
        }
        controller.updateInputConnection(password, EditorInfo().apply {
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
        })
        scope.testScheduler.advanceUntilIdle()
        assertTrue(controller.suggestions.isEmpty())
        assertFalse(controller.applySuggestion("testando"))
    }
}
