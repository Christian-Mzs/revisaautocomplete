package com.example.settings

import android.content.Context
import android.content.Intent
import android.provider.Settings
import android.view.inputmethod.InputMethodManager
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Icon
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.R

@Composable
fun SettingsScreen(isImeEnabled: Boolean, isImeSelected: Boolean, onRefreshImeStatus: () -> Unit,
    account: CodexAccountViewModel = viewModel()) {
    val context = LocalContext.current
    val preferences = remember { OnboardingPreferences(context) }
    val languages = remember { LanguagePreferences(context) }
    var showOnboarding by rememberSaveable { mutableStateOf(!preferences.completed) }
    var replay by rememberSaveable { mutableStateOf(false) }
    var path by rememberSaveable { mutableStateOf(listOf("home")) }
    var enabledCodes by remember { mutableStateOf(languages.translationLanguages.map { it.languageCode }.toSet()) }
    val owner = LocalLifecycleOwner.current
    DisposableEffect(owner, showOnboarding) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                onRefreshImeStatus()
                enabledCodes = languages.translationLanguages.map { it.languageCode }.toSet()
                if (!showOnboarding) account.refresh()
            }
        }
        owner.lifecycle.addObserver(observer)
        onDispose { owner.lifecycle.removeObserver(observer) }
    }
    LaunchedEffect(showOnboarding) { if (!showOnboarding) account.refresh() }
    val enable: () -> Unit = { context.startActivity(Intent(Settings.ACTION_INPUT_METHOD_SETTINGS)) }
    val select: () -> Unit = {
        (context.getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager)?.showInputMethodPicker()
    }
    RevisaTheme {
        if (showOnboarding) {
            OnboardingScreen(isImeEnabled, isImeSelected, replay, enable, select,
                onFinish = { preferences.complete(); showOnboarding = false; replay = false; path = listOf("home") },
                onExitReplay = { showOnboarding = false; replay = false })
        } else {
            val page = path.last()
            val back: () -> Unit = { if (path.size > 1) path = path.dropLast(1) }
            BackHandler(path.size > 1, onBack = back)
            RevisaMenuScreen(page, isImeEnabled, isImeSelected, account.state, enabledCodes,
                onNavigate = { path = path + it }, onBack = back, onEnable = enable, onSelect = select,
                onReplay = { replay = true; showOnboarding = true },
                onLanguage = { code, checked ->
                    val changed = languages.setTranslationEnabled(code, checked)
                    enabledCodes = languages.translationLanguages.map { it.languageCode }.toSet()
                    changed
                }, onLogin = account::login, onCancel = account::cancel, onLogout = account::logout,
                onBrowser = {
                    account.browserIntent()?.let { intent ->
                        runCatching { context.startActivity(intent) }.onFailure { account.browserUnavailable() }
                    }
                })
        }
    }
}

/** Pure presentation accepts only product state; it cannot render a URL or CLI output. */
@Composable
internal fun RevisaMenuScreen(
    page: String, enabled: Boolean, selected: Boolean, account: AccountUiState,
    languageCodes: Set<String>, onNavigate: (String) -> Unit, onBack: () -> Unit,
    onEnable: () -> Unit, onSelect: () -> Unit, onReplay: () -> Unit,
    onLanguage: (String, Boolean) -> Boolean, onLogin: () -> Unit, onCancel: () -> Unit,
    onLogout: () -> Unit, onBrowser: () -> Unit
) {
    val scroll = remember(page) { ScrollState(0) }
    Box(Modifier.fillMaxSize().background(Color(0xFF090C12)), contentAlignment = Alignment.TopCenter) {
        Box(Modifier.widthIn(max = 460.dp).fillMaxSize().background(RevisaColors.Background)
            .drawWithCache {
                val light = Brush.radialGradient(listOf(Color(0xFF1C3358), Color.Transparent),
                    center = Offset.Zero, radius = size.height * .6364f)
                onDrawBehind {
                    withTransform({ scale(size.width / size.height, 1f, Offset.Zero) }) {
                        drawRect(light, size = Size(size.height, size.height))
                    }
                }
            }.safeDrawingPadding()) {
            Column(Modifier.fillMaxSize()) {
                Column(Modifier.weight(1f).verticalScroll(scroll)
                    .padding(start = 24.dp, end = 24.dp, top = 28.dp, bottom = if (page == "languages") 0.dp else 36.dp)) {
                    Row(Modifier.fillMaxWidth().heightIn(min = 44.dp), verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween) {
                        if (page == "home") {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                                Image(painterResource(R.drawable.revisa_icon), null, Modifier.size(39.dp))
                                Copy("Revisa", size = 23.sp, weight = FontWeight.ExtraBold)
                            }
                            Box(Modifier.size(44.dp).clip(RoundedCornerShape(15.dp))
                                .background(Color.White.copy(alpha = .024f))
                                .border(1.dp, Color.White.copy(alpha = .086f), RoundedCornerShape(15.dp))
                                .clickable(role = Role.Button) { onNavigate("settings") }, contentAlignment = Alignment.Center) {
                                Icon(Icons.Outlined.Settings, "Configurações", Modifier.size(23.dp), tint = RevisaColors.Text)
                            }
                        } else BackLink(onBack)
                    }
                    Spacer(Modifier.height(30.dp))
                    when (page) {
                        "home" -> {
                            Spacer(Modifier.height(18.dp))
                            if (enabled && selected) ReadyCard { onNavigate("keyboard") }
                            else KeyboardSetupCard(enabled, selected, onEnable, onSelect, accent = true)
                            Spacer(Modifier.height(18.dp))
                            CodexAccountCard(account, onLogin, onBrowser, onCancel, onLogout)
                            Spacer(Modifier.height(18.dp))
                            RevisaCard {
                                Heading("Idiomas de tradução", 21.sp)
                                Spacer(Modifier.height(10.dp))
                                Copy("Escolha quais idiomas aparecem quando você toca em Traduzir.", size = 12.sp, color = RevisaColors.Muted)
                                MenuRow("Gerenciar idiomas", "${languageCodes.size} idiomas selecionados") { onNavigate("languages") }
                            }
                            Spacer(Modifier.height(18.dp))
                        }
                        "settings" -> {
                            Heading("Configurações")
                            GroupHeading("Ajuda")
                            RevisaCard(padding = 20.dp, verticalPadding = 0.dp) {
                                MenuRow("Como usar") { onNavigate("help") }
                                Divider()
                                MenuRow("Repetir apresentação", onClick = onReplay)
                            }
                            GroupHeading("Privacidade")
                            RevisaCard(padding = 20.dp, verticalPadding = 0.dp) { MenuRow("Privacidade") { onNavigate("privacy") } }
                        }
                        "keyboard" -> {
                            Heading("Seu teclado")
                            Spacer(Modifier.height(32.dp))
                            KeyboardSetupCard(enabled, selected, onEnable, onSelect)
                        }
                        "languages" -> LanguageSettingsPage(languageCodes, onLanguage)
                        "help" -> HelpPage()
                        "privacy" -> PrivacyPage()
                    }
                }
                if (page == "languages") Box(Modifier.background(RevisaColors.Background).padding(24.dp, 10.dp, 24.dp, 12.dp)) {
                    RevisaButton("Concluir", onClick = onBack)
                }
            }
        }
    }
}

@Composable
internal fun BackLink(onClick: () -> Unit) {
    Box(Modifier.heightIn(min = 44.dp).clickable(role = Role.Button, onClick = onClick)
        .padding(vertical = 12.dp), contentAlignment = Alignment.CenterStart) {
        Copy("‹ Voltar", size = 14.sp, weight = FontWeight.SemiBold)
    }
}

@Composable
private fun GroupHeading(text: String) {
    Spacer(Modifier.height(25.dp))
    androidx.compose.material3.Text(text.uppercase(), color = Color.White, fontSize = 11.sp, lineHeight = 13.sp, letterSpacing = 1.sp)
    Spacer(Modifier.height(18.dp))
}

@Composable
private fun ReadyCard(onConfigure: () -> Unit) {
    Column(Modifier.fillMaxWidth().testTag("status_ready_card").clip(RoundedCornerShape(18.dp))
        .background(Brush.linearGradient(listOf(Color(0xFF183D2C), Color(0xFF14271E))))
        .border(1.dp, Color(0xFF54BD80).copy(alpha = .333f), RoundedCornerShape(18.dp)).padding(18.dp, 15.dp)) {
        Copy("✓ Revisa pronto", size = 14.sp, weight = FontWeight.Bold, color = Color(0xFFA7DFB9))
        Box(Modifier.heightIn(min = 44.dp).clickable(role = Role.Button, onClick = onConfigure), contentAlignment = Alignment.CenterStart) {
            Copy("Configurar teclado", size = 12.sp, color = Color(0xFFC2D7C9), weight = FontWeight.SemiBold)
        }
    }
}

@Composable
private fun KeyboardSetupCard(enabled: Boolean, selected: Boolean, onEnable: () -> Unit, onSelect: () -> Unit,
    accent: Boolean = false) {
    RevisaCard(padding = 20.dp, accent = accent, verticalPadding = 0.dp) {
        MenuRow(if (enabled) "✓ Revisa ativado" else "Ativar o Revisa", if (accent) "Configurações do Android" else null,
            Modifier.testTag("btn_activate_ime"), onClick = onEnable)
        Divider()
        MenuRow(if (selected) "✓ Revisa selecionado" else "Escolher o Revisa", if (accent) "Seletor de teclado do Android" else null,
            Modifier.testTag("btn_select_ime"), onClick = onSelect)
    }
}

@Composable
internal fun CodexAccountCard(state: AccountUiState, onLogin: () -> Unit, onBrowser: () -> Unit,
    onCancel: () -> Unit, onLogout: () -> Unit) {
    RevisaCard(accent = true, padding = 20.dp) {
        Spacer(Modifier.height(3.dp))
        Heading("Inteligência Artificial", 21.sp)
        Spacer(Modifier.height(14.dp))
        StatusBadge(state)
        Spacer(Modifier.height(15.dp))
        Copy("Use sua conta ChatGPT para corrigir e traduzir quando precisar.", size = 12.sp, color = Color(0xFFD9E3F2))
        if (state.busy) {
            ProgressLine(when (state.operation) {
                AccountOperation.PREPARING -> "Preparando recursos do Revisa…"
                AccountOperation.CHECKING -> "Verificando conta…"
                AccountOperation.LOGOUT -> "Saindo da conta…"
                else -> if (state.canOpenBrowser) "Conclua o login no navegador" else "Iniciando login…"
            })
            if (state.operation == AccountOperation.LOGIN) {
                RevisaButton("Abrir login no navegador", enabled = state.canOpenBrowser, onClick = onBrowser)
                Spacer(Modifier.height(12.dp))
            }
            RevisaButton("Cancelar", secondary = true, cancel = true, onClick = onCancel)
        } else {
            Spacer(Modifier.height(20.dp))
            if (state.connected) RevisaButton("Sair da conta", secondary = true, onClick = onLogout)
            else RevisaButton("Entrar com ChatGPT", onClick = onLogin)
        }
        state.error?.let { Spacer(Modifier.height(12.dp)); Copy(it, size = 13.sp, color = Color(0xFFE4A0A4)) }
        Spacer(Modifier.height(3.dp))
    }
}

@Composable
private fun HelpPage() {
    Heading("Como usar")
    Spacer(Modifier.height(14.dp))
    // The menu HTML embeds this exact pose as its help mascot.
    Mascot(R.drawable.revisa_wink, 122.dp)
    Spacer(Modifier.height(18.dp))
    RevisaCard {
        val entries = listOf(
            "Ative o Revisa" to "Permita o teclado nas configurações do Android.",
            "Escolha o Revisa" to "Selecione-o como seu teclado atual.",
            "Conecte o ChatGPT" to "Entre uma vez com sua conta ChatGPT para usar Corrigir e Traduzir.",
            "Corrija" to "Toque em Corrigir para corrigir a escrita sem mudar o idioma do texto.",
            "Traduza" to "Toque em Traduzir e escolha o idioma de destino.",
            "Confira" to "O texto só é substituído depois que você confirma.")
        entries.forEachIndexed { i, (title, body) ->
            if (i > 0) Divider()
            Column(Modifier.padding(vertical = 17.dp)) {
                Heading("${i + 1}. $title", 16.sp)
                Spacer(Modifier.height(10.dp))
                Copy(body, size = 14.sp, color = Color(0xFFC5CBD5))
            }
        }
    }
}

@Composable
private fun PrivacyPage() {
    Eyebrow("Você no controle")
    Heading("Privacidade")
    Spacer(Modifier.height(32.dp))
    RevisaCard {
        Heading("Somente quando você pedir", 21.sp)
        Spacer(Modifier.height(10.dp))
        Copy("O texto só é enviado ao ChatGPT/OpenAI quando você toca em Corrigir ou Traduzir.")
        Spacer(Modifier.height(10.dp))
        Copy("Digitar normalmente não dispara uma solicitação ao ChatGPT.", color = RevisaColors.Muted)
        Spacer(Modifier.height(10.dp))
        Copy("Em campos de senha identificados pelo Android, Corrigir e Traduzir ficam desativados e o conteúdo não é enviado ao Codex/OpenAI.", color = RevisaColors.Muted)
    }
    Spacer(Modifier.height(18.dp))
    RevisaCard {
        Heading("Confira antes", 21.sp)
        Spacer(Modifier.height(10.dp))
        Copy("Você confere o resultado antes de substituir o texto.")
    }
}
