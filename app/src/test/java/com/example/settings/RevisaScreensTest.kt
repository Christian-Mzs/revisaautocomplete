package com.example.settings

import android.graphics.Bitmap
import androidx.compose.runtime.*
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w412dp-h892dp-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class RevisaScreensTest {
    @get:Rule val compose = createComposeRule()

    @Test fun `IME completion follows real flags and demos never require an account`() {
        var enabled by mutableStateOf(false)
        var selected by mutableStateOf(false)
        var settingsOpened = 0; var pickerOpened = 0; var finishes = 0
        compose.setContent { RevisaTheme { OnboardingScreen(enabled, selected, false,
            { settingsOpened++ }, { pickerOpened++ }, { finishes++ }, {}) } }
        primary(); primary(); primary()
        compose.onNodeWithText("Ative o Revisa").assertExists()
        primary()
        assertEquals(1, settingsOpened)
        compose.onNodeWithText("Etapa concluída").assertDoesNotExist()
        compose.runOnIdle { enabled = true }
        compose.onNodeWithText("Etapa concluída").assertExists()
        primary(); primary()
        assertEquals(1, pickerOpened)
        compose.onNodeWithText("Revisa selecionado").assertDoesNotExist()
        compose.runOnIdle { selected = true }
        compose.onNodeWithText("Revisa selecionado").assertExists()
        primary(); primary()
        compose.onNodeWithText("Eu não sei se ele vai vir amanhã.").assertExists()
        primary(); primary()
        compose.onNodeWithText("Can we talk tomorrow after lunch?").assertExists()
        primary(); primary()
        assertEquals(1, finishes)
    }

    @Test fun `replaying presentation can exit without changing completion`() {
        var exits = 0
        compose.setContent { RevisaTheme { OnboardingScreen(true, true, true, {}, {}, {}, { exits++ }) } }
        compose.onNodeWithText("‹ Voltar").performClick()
        assertEquals(1, exits)
    }

    @Test fun `menu compacts completed setup and account exposes only product controls`() {
        var state by mutableStateOf(AccountUiState())
        var ready by mutableStateOf(false)
        var loginCalls = 0; var browserCalls = 0; var cancelCalls = 0
        compose.setContent { RevisaTheme { MenuFixture(enabled = ready, selected = ready, state = state,
            login = { loginCalls++ }, browser = { browserCalls++ }, cancel = { cancelCalls++ }) } }
        compose.onNodeWithText("Ativar o Revisa").assertExists()
        compose.onNodeWithText("Entrar com ChatGPT").performScrollTo().performClick()
        assertEquals(1, loginCalls)
        compose.runOnIdle { ready = true; state = AccountUiState(operation = AccountOperation.LOGIN, canOpenBrowser = true) }
        compose.onNodeWithText("✓ Revisa pronto").assertExists()
        compose.onNodeWithText("Ativar o Revisa").assertDoesNotExist()
        compose.onNodeWithText("Abrir login no navegador").performScrollTo().performClick()
        assertEquals(1, browserCalls)
        compose.onNodeWithText("Cancelar").performScrollTo().performClick()
        assertEquals(1, cancelCalls)
        for (forbidden in listOf("https://", "localhost", "Starting local login", "device", "código de dispositivo", "Alpine", "PRoot", "Codex CLI")) {
            compose.onAllNodes(hasText(forbidden, substring = true, ignoreCase = true)).assertCountEquals(0)
        }
    }

    @Test fun `language changes use the existing storage and refuse removing last choice`() {
        val context = androidx.test.core.app.ApplicationProvider.getApplicationContext<android.content.Context>()
        val preferences = LanguagePreferences(context)
        preferences.translationLanguages.filter { it.languageCode != "en" }.forEach { preferences.setTranslationEnabled(it.languageCode, false) }
        var codes by mutableStateOf(setOf("en"))
        compose.setContent { RevisaTheme { MenuFixture(page = "languages", codes = codes, language = { code, checked ->
            val changed = preferences.setTranslationEnabled(code, checked)
            codes = preferences.translationLanguages.map { it.languageCode }.toSet(); changed
        }) } }
        compose.onNodeWithText("Inglês").performScrollTo().performClick()
        assertEquals(setOf("en"), codes)
        compose.onNodeWithText("Espanhol").performScrollTo().performClick()
        assertEquals(setOf("en", "es"), LanguagePreferences(context).translationLanguages.map { it.languageCode }.toSet())
    }

    @Test fun `render reference screens for visual review`() {
        var page by mutableStateOf("home")
        var ready by mutableStateOf(false)
        var account by mutableStateOf(AccountUiState())
        compose.setContent { RevisaTheme { MenuFixture(page, ready, ready, account) } }
        capture("home-setup")
        compose.runOnIdle { ready = true }; capture("home-ready")
        compose.runOnIdle { account = AccountUiState(operation = AccountOperation.PREPARING) }; capture("home-preparing")
        compose.runOnIdle { account = AccountUiState(operation = AccountOperation.LOGIN, canOpenBrowser = true) }; capture("home-login")
        compose.runOnIdle { account = AccountUiState(connected = true) }; capture("home-connected")
        for (destination in listOf("settings", "help", "privacy", "languages", "keyboard")) {
            compose.runOnIdle { page = destination }; capture(destination)
        }
    }

    @Test fun `render onboarding for visual review`() {
        compose.setContent { RevisaTheme { OnboardingScreen(true, true, false, {}, {}, {}, {}) } }
        capture("onboarding-0"); primary(); capture("onboarding-0-message-2")
        primary(); capture("onboarding-1"); primary(); capture("onboarding-2-complete")
        primary(); capture("onboarding-3-complete"); primary(); capture("onboarding-4")
        primary(); capture("onboarding-4-result"); primary(); capture("onboarding-5")
        primary(); capture("onboarding-5-result"); primary(); capture("onboarding-6")
    }

    @Test @Config(qualifiers = "w320dp-h568dp-mdpi")
    fun `small screen retains accessible onboarding action`() {
        compose.setContent { RevisaTheme { OnboardingScreen(false, false, false, {}, {}, {}, {}) } }
        compose.onNodeWithTag("onboarding_primary").assertIsDisplayed()
        capture("small-onboarding")
        primary(); primary(); primary()
        compose.onNodeWithTag("onboarding_primary").assertIsDisplayed()
        capture("small-activation")
    }

    private fun primary() { compose.onNodeWithTag("onboarding_primary").performClick(); compose.waitForIdle() }
    private fun capture(name: String) {
        compose.waitForIdle()
        val bitmap = compose.onRoot().captureToImage().asAndroidBitmap()
        val destination = File("build/reports/revisa-ui/$name.png")
        destination.parentFile.mkdirs()
        destination.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        assertTrue(bitmap.width > 0 && bitmap.height > 0)
    }
}

@Composable
private fun MenuFixture(page: String = "home", enabled: Boolean = true, selected: Boolean = true,
    state: AccountUiState = AccountUiState(), codes: Set<String> = setOf("en", "es", "pt", "fr", "de", "it", "zh-CN", "ja", "ko"),
    login: () -> Unit = {}, browser: () -> Unit = {}, cancel: () -> Unit = {},
    language: (String, Boolean) -> Boolean = { _, _ -> true }) {
    RevisaMenuScreen(page, enabled, selected, state, codes, {}, {}, {}, {}, {}, language, login, cancel, {}, browser)
}
