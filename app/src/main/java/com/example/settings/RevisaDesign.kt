package com.example.settings

import androidx.annotation.DrawableRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.Font
import com.example.R
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// The supplied HTML resolves system-ui to DejaVu Sans in its rendered reference.
// Bundle it for consistent layout across Android vendors; this theme is app UI only.
internal val RevisaFont = FontFamily(
    Font(R.font.revisa_reference_regular, FontWeight.Normal),
    Font(R.font.revisa_reference_regular, FontWeight.Medium),
    Font(R.font.revisa_reference_bold, FontWeight.SemiBold),
    Font(R.font.revisa_reference_bold, FontWeight.Bold),
    Font(R.font.revisa_reference_bold, FontWeight.ExtraBold),
    Font(R.font.revisa_reference_bold, FontWeight.Black)
)

internal object RevisaColors {
    val Blue = Color(0xFF1768FF)
    val BlueDepth = Color(0xFF0D4FD0)
    val Background = Color(0xFF101620)
    val Onboarding = Color(0xFF101216)
    val Surface = Color(0xFF1B1F27)
    val Line = Color(0xFF2A303A)
    val Text = Color(0xFFF1F2F4)
    val Muted = Color(0xFFAFB5C0)
    val Green = Color(0xFF80D39A)
    val Paper = Color(0xFFF0F1F3)
    val Ink = Color(0xFF242832)
}

@Composable
internal fun RevisaTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(primary = RevisaColors.Blue, onPrimary = Color.White,
            background = RevisaColors.Background, surface = RevisaColors.Surface,
            onSurface = RevisaColors.Text, onBackground = RevisaColors.Text),
        typography = Typography(bodyLarge = androidx.compose.ui.text.TextStyle(
            fontFamily = RevisaFont, fontSize = 15.sp, lineHeight = 23.sp)),
        content = content
    )
}

@Composable
internal fun Copy(text: String, modifier: Modifier = Modifier, size: TextUnit = 15.sp,
    color: Color = RevisaColors.Text, weight: FontWeight = FontWeight.Normal,
    lineHeight: TextUnit = (size.value * 1.55f).sp, align: TextAlign? = null) {
    Text(text, modifier, color = color, fontSize = size, fontWeight = weight,
        fontFamily = RevisaFont, lineHeight = lineHeight, textAlign = align)
}

@Composable
internal fun Heading(text: String, size: TextUnit = 34.sp, centered: Boolean = false,
    color: Color = RevisaColors.Text, modifier: Modifier = Modifier) {
    Text(text, modifier, color = color, fontSize = size, fontWeight = FontWeight.ExtraBold,
        fontFamily = RevisaFont, lineHeight = (size.value * if (size.value <= 21f) 1.2f else 1.12f).sp, letterSpacing = (if (size.value <= 21f) -.4f else -size.value * .034f).sp,
        textAlign = if (centered) TextAlign.Center else TextAlign.Start)
}

@Composable
internal fun Eyebrow(text: String) {
    Text(text.uppercase(), color = Color(0xFF96BCFF), fontSize = 11.sp,
        fontWeight = FontWeight.ExtraBold, letterSpacing = 0.8.sp)
    Spacer(Modifier.height(8.dp))
}

@Composable
internal fun RevisaButton(text: String, modifier: Modifier = Modifier, enabled: Boolean = true,
    secondary: Boolean = false, cancel: Boolean = false, onboarding: Boolean = false,
    onClick: () -> Unit) {
    val radius = RoundedCornerShape(17.dp)
    val color = when { cancel -> Color(0xFF38262C); secondary -> Color.White.copy(alpha = .03f); else -> RevisaColors.Blue }
    val bottom = if (!secondary && !cancel) (if (onboarding) 7.dp else 5.dp) else 0.dp
    Box(modifier.fillMaxWidth().padding(bottom = bottom)) {
        if (bottom > 0.dp) Box(Modifier.matchParentSize().offset(y = bottom).clip(radius)
            .background(RevisaColors.BlueDepth.copy(alpha = if (enabled) 1f else .45f)))
        Box(Modifier.fillMaxWidth().heightIn(min = if (onboarding) 56.dp else if (cancel) 46.dp else 54.dp)
            .clip(radius).background(color.copy(alpha = color.alpha * if (enabled) 1f else .45f))
            .then(if (secondary || cancel) Modifier.border(1.dp,
                if (cancel) Color(0xFF634049) else Color.White.copy(alpha = .094f), radius) else Modifier)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp), contentAlignment = Alignment.Center) {
            Copy(text, size = if (onboarding) 16.sp else 15.sp,
                color = (if (cancel) Color(0xFFE4A0A4) else Color.White).copy(alpha = if (enabled) 1f else .45f),
                weight = FontWeight.ExtraBold, align = TextAlign.Center, lineHeight = 21.sp)
        }
    }
}

@Composable
internal fun RevisaCard(modifier: Modifier = Modifier, padding: Dp = 21.dp,
    paper: Boolean = false, accent: Boolean = false, verticalPadding: Dp = padding, content: @Composable ColumnScope.() -> Unit) {
    val shape = RoundedCornerShape(if (paper) 25.dp else 23.dp)
    val fill = if (accent) Brush.linearGradient(listOf(RevisaColors.Blue.copy(alpha = .125f),
        RevisaColors.Blue.copy(alpha = .031f))) else Brush.linearGradient(listOf(
        if (paper) RevisaColors.Paper else Color.White.copy(alpha = .016f),
        if (paper) RevisaColors.Paper else Color.White.copy(alpha = .016f)))
    Column(modifier.fillMaxWidth().clip(shape).background(fill)
        .then(if (paper) Modifier else Modifier.border(1.dp,
            if (accent) Color(0xFF518FFF).copy(alpha = .25f) else Color.White.copy(alpha = .071f), shape))
        .padding(horizontal = padding, vertical = verticalPadding), content = content)
}

@Composable
internal fun MenuRow(title: String, subtitle: String? = null, modifier: Modifier = Modifier,
    enabled: Boolean = true, onClick: () -> Unit) {
    Row(modifier.fillMaxWidth().heightIn(min = 60.dp).clickable(enabled = enabled, role = Role.Button, onClick = onClick)
        .padding(vertical = 18.dp), verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(15.dp)) {
        Column(Modifier.weight(1f)) {
            Copy(title, weight = FontWeight.Bold, lineHeight = 18.sp, color = RevisaColors.Text.copy(alpha = if (enabled) 1f else .45f))
            if (subtitle != null) { Spacer(Modifier.height(5.dp)); Copy(subtitle, size = 12.sp, lineHeight = 14.4.sp, color = RevisaColors.Muted) }
        }
        Copy("›", size = 24.sp, color = Color(0xFF96BCFF))
    }
}

@Composable
internal fun Divider(paper: Boolean = false) {
    HorizontalDivider(thickness = 1.dp, color = if (paper) Color(0xFFD6D9DE) else RevisaColors.Line)
}

@Composable
internal fun Mascot(@DrawableRes resource: Int, height: Dp, modifier: Modifier = Modifier,
    description: String = "Mascote do Revisa") {
    Image(painterResource(resource), description, modifier.fillMaxWidth().height(height), contentScale = ContentScale.Fit)
}

@Composable
internal fun StatusBadge(state: AccountUiState) {
    val shape = RoundedCornerShape(12.dp)
    Box(Modifier.clip(shape).background(if (state.connected) Color(0xFF183D2C) else Color.White.copy(alpha = .039f))
        .border(1.dp, if (state.connected) Color(0xFF54BD80).copy(alpha = .333f) else Color.White.copy(alpha = .125f), shape)
        .heightIn(min = 38.dp).padding(horizontal = 13.dp, vertical = 8.dp)) {
        Copy(state.label, size = 14.sp, weight = FontWeight.ExtraBold,
            color = if (state.connected) Color(0xFFB1F0C9) else Color(0xFFE4EAF5), lineHeight = 20.sp)
    }
}

@Composable
internal fun ProgressLine(text: String) {
    Row(Modifier.padding(vertical = 19.dp), verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(9.dp)) {
        CircularProgressIndicator(Modifier.size(17.dp), color = Color(0xFF9FC0FF), strokeWidth = 2.dp)
        Copy(text, size = 13.sp)
    }
}
