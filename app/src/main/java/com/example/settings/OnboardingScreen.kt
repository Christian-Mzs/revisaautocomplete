package com.example.settings

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Keyboard
import androidx.compose.material3.Icon
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R

@Composable
internal fun OnboardingScreen(enabled: Boolean, selected: Boolean, replay: Boolean,
    onEnable: () -> Unit, onSelect: () -> Unit, onFinish: () -> Unit, onExitReplay: () -> Unit) {
    var step by rememberSaveable { mutableIntStateOf(0) }
    var introIndex by rememberSaveable { mutableIntStateOf(0) }
    var corrected by rememberSaveable { mutableStateOf(false) }
    var translated by rememberSaveable { mutableStateOf(false) }
    val completed = (step == 2 && enabled) || (step == 3 && selected)
    val back: () -> Unit = {
        when { step > 0 -> step--; introIndex > 0 -> introIndex--; replay -> onExitReplay() }
    }
    BackHandler(step > 0 || introIndex > 0 || replay, onBack = back)
    val next: () -> Unit = {
        when (step) {
            0 -> if (introIndex == 0) introIndex = 1 else step++
            1 -> step++
            2 -> if (enabled) step++ else onEnable()
            3 -> if (selected) step++ else onSelect()
            4 -> if (corrected) step++ else corrected = true
            5 -> if (translated) step++ else translated = true
            6 -> onFinish()
        }
    }
    val label = when (step) {
        0 -> "Continuar"
        1 -> "Começar configuração"
        2 -> if (enabled) "Continuar" else "Abrir configurações do Android"
        3 -> if (selected) "Continuar" else "Abrir seletor de teclado"
        4 -> if (corrected) "Continuar" else "Corrigir esta mensagem"
        5 -> if (translated) "Continuar" else "Traduzir para inglês"
        else -> "Ir para o Revisa"
    }
    Box(Modifier.fillMaxSize().background(Color(0xFF0A0C10)), contentAlignment = Alignment.TopCenter) {
        BoxWithConstraints(Modifier.widthIn(max = 480.dp).fillMaxSize()
            .background(Brush.verticalGradient(listOf(RevisaColors.Onboarding, Color(0xFF0D0F13))))
            .safeDrawingPadding()) {
            val compact = maxHeight < 760.dp
            Column(Modifier.fillMaxSize()) {
                Column(Modifier.padding(start = 22.dp, end = 22.dp, top = 18.dp)) {
                    Box(Modifier.height(34.dp)) {
                        if (step > 0 || introIndex > 0 || replay) Box(Modifier.heightIn(min = 34.dp)
                            .clickable(role = Role.Button, onClick = back).padding(horizontal = 4.dp, vertical = 7.dp)) {
                            Copy("‹ Voltar", size = 14.sp, color = Color(0xFFC8CCD3), weight = FontWeight.Bold, lineHeight = 20.sp)
                        }
                    }
                    Spacer(Modifier.height(10.dp))
                    Row(Modifier.fillMaxWidth().semantics { contentDescription = "Progresso da apresentação: ${step + 1} de 7" },
                        horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        repeat(7) { index -> Box(Modifier.weight(1f).height(4.dp).clip(CircleShape)
                            .background(when { index < step -> Color(0xFF5B7FBF); index == step -> RevisaColors.Blue; else -> Color(0xFF252A31) })) }
                    }
                }
                BoxWithConstraints(Modifier.weight(1f).fillMaxWidth()) {
                    val available = maxHeight
                    val scroll = rememberScrollState()
                    LaunchedEffect(step) { scroll.scrollTo(0) }
                    Column(Modifier.fillMaxWidth().verticalScroll(scroll).heightIn(min = available)
                        .padding(horizontal = 24.dp, vertical = if (compact) 12.dp else 20.dp),
                        verticalArrangement = Arrangement.Center) {
                        when {
                            completed -> CompletedStep(if (step == 2) "Etapa concluída" else "Revisa selecionado")
                            step == 0 -> IntroSlide(introIndex, compact)
                            step == 1 -> PreparationSlide(compact)
                            step == 2 -> EnableSlide(compact)
                            step == 3 -> SelectSlide(compact)
                            step == 4 -> DemoSlide(false, corrected, compact)
                            step == 5 -> DemoSlide(true, translated, compact)
                            step == 6 -> FinishSlide(compact)
                        }
                    }
                }
                Column(Modifier.padding(start = 24.dp, end = 24.dp,
                    bottom = if (completed) 48.dp else if (compact) 31.dp else 36.dp)) {
                    RevisaButton(label, Modifier.testTag("onboarding_primary"), onboarding = true, onClick = next)
                }
            }
        }
    }
}

@Composable
private fun HeroMascot(resource: Int, height: androidx.compose.ui.unit.Dp) {
    Box(Modifier.fillMaxWidth().height(height), contentAlignment = Alignment.Center) {
        Box(Modifier.size(235.dp, 176.dp).background(Brush.radialGradient(listOf(
            Color(0xFF3475FF).copy(alpha = .26f), Color(0xFF1E4585).copy(alpha = .1f), Color.Transparent))))
        Mascot(resource, height)
    }
}

@Composable
private fun IntroSlide(index: Int, compact: Boolean) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
        HeroMascot(R.drawable.revisa_onboarding_stand, if (compact) 202.dp else 255.dp)
        Spacer(Modifier.height(if (compact) 9.dp else 17.dp))
        Heading("Olá, eu sou o Revisa.", if (compact) 34.sp else 40.sp, centered = true,
            modifier = Modifier.padding(horizontal = 8.dp))
        Spacer(Modifier.height(24.dp))
        Box(Modifier.fillMaxWidth().heightIn(min = 150.dp).padding(horizontal = 8.dp), contentAlignment = Alignment.Center) {
            AnimatedContent(index, transitionSpec = { fadeIn(tween(300)) togetherWith fadeOut(tween(100)) }, label = "Mensagem de apresentação") { current ->
                val message = buildAnnotatedString {
                    withStyle(SpanStyle(fontWeight = FontWeight.ExtraBold)) {
                        append(if (current == 0) "Um teclado simples, sem anúncios" else "Precisa corrigir ou traduzir?")
                    }
                    append(if (current == 0) " e com suporte à inteligência artificial." else " Você escolhe quando pedir ajuda.")
                }
                androidx.compose.material3.Text(message, color = RevisaColors.Text, fontSize = 22.sp,
                    lineHeight = 31.9.sp, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center)
            }
        }
    }
}

@Composable
private fun PreparationSlide(compact: Boolean) {
    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        HeroMascot(R.drawable.revisa_onboarding_point, if (compact) 186.dp else 232.dp)
        Spacer(Modifier.height(if (compact) 9.dp else 17.dp))
        Heading("Vamos começar?", if (compact) 33.sp else 38.sp, centered = true)
        Spacer(Modifier.height(14.dp))
        Copy("Eu te acompanho na configuração,\npasso a passo.", Modifier.widthIn(max = 300.dp), size = 16.sp, align = TextAlign.Center)
        Row(Modifier.padding(top = 23.dp), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
            SetupChip("Ativar"); Copy("→", size = 13.sp); SetupChip("Escolher")
        }
    }
}

@Composable
private fun SetupChip(text: String) {
    Row(Modifier.clip(CircleShape).background(Color(0xFF181C23)).border(1.dp, Color(0xFF2A3038), CircleShape)
        .padding(12.dp, 10.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(7.dp)) {
        Box(Modifier.size(7.dp).clip(CircleShape).background(RevisaColors.Blue))
        Copy(text, size = 13.sp, weight = FontWeight.ExtraBold)
    }
}

@Composable
private fun CompletedStep(text: String) {
    Column(Modifier.fillMaxWidth().heightIn(min = 330.dp), verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally) {
        Box(Modifier.size(74.dp).clip(CircleShape).background(RevisaColors.Green.copy(alpha = .06f)), contentAlignment = Alignment.Center) {
            Box(Modifier.size(58.dp).clip(CircleShape).background(Color(0xFF183425)), contentAlignment = Alignment.Center) {
                Copy("✓", size = 27.sp, color = RevisaColors.Green, weight = FontWeight.ExtraBold)
            }
        }
        Spacer(Modifier.height(15.dp))
        Copy(text, size = 22.sp, weight = FontWeight.ExtraBold, align = TextAlign.Center)
    }
}

@Composable
private fun EnableSlide(compact: Boolean) {
    Eyebrow("Etapa 1 de 2")
    Heading("Ative o Revisa", if (compact) 26.sp else 29.sp)
    Spacer(Modifier.height(26.dp))
    RevisaCard(paper = true, padding = if (compact) 17.dp else 18.dp) {
        Instruction(1) {
            Copy("Clique em", size = 14.5.sp, color = RevisaColors.Ink, weight = FontWeight.Bold)
            Spacer(Modifier.height(5.dp))
            Box(Modifier.clip(RoundedCornerShape(11.dp)).background(RevisaColors.Blue).padding(12.dp, 8.dp)) {
                Copy("Abrir configurações do Android", size = 12.sp, weight = FontWeight.ExtraBold)
            }
        }
        Divider(paper = true)
        Instruction(2) { Copy("Na tela que abrir, ative Revisa e confirme o aviso do sistema.", size = 14.5.sp, color = Color(0xFF4C535D), lineHeight = 21.sp) }
        Divider(paper = true)
        Instruction(3) { Copy("Volte para este aplicativo.", size = 14.5.sp, color = Color(0xFF4C535D), lineHeight = 21.sp) }
    }
}

@Composable
private fun Instruction(number: Int, content: @Composable ColumnScope.() -> Unit) {
    Row(Modifier.padding(vertical = 11.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Box(Modifier.padding(top = 1.dp).size(27.dp).clip(CircleShape).background(Color(0xFFDBE6FF)), contentAlignment = Alignment.Center) {
            Copy(number.toString(), size = 12.sp, weight = FontWeight.Black, color = Color(0xFF145BD8))
        }
        Column(Modifier.weight(1f), content = content)
    }
}

@Composable
private fun SelectSlide(compact: Boolean) {
    Eyebrow("Etapa 2 de 2")
    Heading("Escolha o Revisa", if (compact) 26.sp else 29.sp)
    Spacer(Modifier.height(26.dp))
    RevisaCard(paper = true, padding = if (compact) 17.dp else 21.dp) {
        Box(Modifier.size(45.dp).clip(RoundedCornerShape(15.dp)).background(Color(0xFFDBE6FF)), contentAlignment = Alignment.Center) {
            Icon(Icons.Outlined.Keyboard, null, Modifier.size(23.dp), tint = Color(0xFF145BD8))
        }
        Spacer(Modifier.height(14.dp))
        Heading("Abra o seletor de teclado", 20.sp, color = RevisaColors.Ink)
        Spacer(Modifier.height(8.dp))
        Copy("A escolha acontece na janela do próprio Android.", size = 14.sp, color = Color(0xFF4C535D), lineHeight = 21.sp)
    }
}

@Composable
private fun DemoSlide(translate: Boolean, result: Boolean, compact: Boolean) {
    Eyebrow("Ferramenta")
    Heading(if (translate) "Traduzir" else "Corrigir", if (compact) 26.sp else 29.sp)
    Spacer(Modifier.height(18.dp))
    RevisaCard(paper = true, padding = if (compact) 15.dp else 18.dp) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Copy("MENSAGEM", size = 11.sp, weight = FontWeight.ExtraBold, color = Color(0xFF7A818C))
            Box(Modifier.clip(CircleShape).background(Color(0xFFDCE7FF)).padding(9.dp, 6.dp)) {
                Copy(if (translate) "Para inglês" else "Corrigir", size = 11.sp, color = Color(0xFF145BD8), weight = FontWeight.ExtraBold)
            }
        }
        Spacer(Modifier.height(13.dp))
        Copy(if (translate) "Podemos conversar amanhã depois do almoço?" else "eu nao sei se ele vai vim amanha",
            Modifier.heightIn(min = 58.dp), size = if (compact) 17.sp else 19.sp, color = RevisaColors.Ink, lineHeight = if (compact) 26.sp else 29.sp)
        if (result) {
            Spacer(Modifier.height(15.dp))
            Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(Color(0xFFDFE9FF))
                .border(1.dp, Color(0xFFC7D8FB), RoundedCornerShape(18.dp)).padding(14.dp)) {
                Copy(if (translate) "TRADUÇÃO" else "SUGESTÃO DO REVISA", size = 11.sp, weight = FontWeight.ExtraBold, color = Color(0xFF145BD8))
                Spacer(Modifier.height(7.dp))
                Copy(if (translate) "Can we talk tomorrow after lunch?" else "Eu não sei se ele vai vir amanhã.",
                    size = 15.5.sp, color = Color(0xFF282D36), lineHeight = 23.sp)
            }
        }
    }
    Spacer(Modifier.height(13.dp))
    Copy(if (translate) "Escolha o idioma de destino na hora de traduzir." else "Com seleção, só o trecho escolhido seria processado.",
        Modifier.fillMaxWidth(), size = 11.5.sp, align = TextAlign.Center)
}

@Composable
private fun FinishSlide(compact: Boolean) {
    HeroMascot(R.drawable.revisa_onboarding_run, if (compact) 188.dp else 232.dp)
    Spacer(Modifier.height(if (compact) 9.dp else 17.dp))
    Box {
        Canvas(Modifier.offset(x = 52.dp, y = (-10).dp).size(20.dp)) {
            drawPath(Path().apply { moveTo(0f, size.height / 2); lineTo(size.width / 2, 0f); lineTo(size.width, size.height / 2); close() }, RevisaColors.Surface)
        }
        Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(26.dp)).background(RevisaColors.Surface)
            .border(1.dp, RevisaColors.Line, RoundedCornerShape(26.dp)).padding(if (compact) 19.dp else 22.dp)) {
            Eyebrow("Tudo pronto")
            Heading("Agora é só escrever.", if (compact) 33.sp else 38.sp)
            Spacer(Modifier.height(14.dp))
            Copy("Quando quiser, as ferramentas do Revisa ficam por perto.", size = 16.sp)
        }
    }
}
