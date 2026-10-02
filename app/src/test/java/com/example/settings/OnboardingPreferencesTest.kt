package com.example.settings

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class OnboardingPreferencesTest {
    @Test fun `first opening persists completion without resetting any other preferences`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        context.getSharedPreferences("revisa_presentation", Context.MODE_PRIVATE).edit().clear().commit()
        val unrelated = context.getSharedPreferences("codex_test_session", Context.MODE_PRIVATE)
        unrelated.edit().putString("session", "keep").commit()
        val languages = LanguagePreferences(context)
        languages.setTranslationEnabled("ru", true)
        val before = languages.translationLanguages
        assertFalse(OnboardingPreferences(context).completed)
        OnboardingPreferences(context).complete()
        assertTrue(OnboardingPreferences(context).completed)
        assertEquals(before, LanguagePreferences(context).translationLanguages)
        assertEquals("keep", unrelated.getString("session", null))
    }
}
