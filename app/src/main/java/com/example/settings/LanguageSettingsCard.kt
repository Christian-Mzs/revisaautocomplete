package com.example.settings

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.translation.SupportedLanguages

// Labels match the Portuguese reference. Codes/order/availability still come from SupportedLanguages.
private val languageLabels = mapOf("en" to "Inglês", "es" to "Espanhol", "pt" to "Português",
    "fr" to "Francês", "de" to "Alemão", "it" to "Italiano", "zh-CN" to "Chinês simplificado",
    "ja" to "Japonês", "ko" to "Coreano", "zh-TW" to "Chinês tradicional", "ar" to "Árabe",
    "ru" to "Russo", "hi" to "Hindi", "nl" to "Holandês", "sv" to "Sueco", "no" to "Norueguês",
    "da" to "Dinamarquês", "fi" to "Finlandês", "pl" to "Polonês", "tr" to "Turco", "el" to "Grego",
    "uk" to "Ucraniano", "he" to "Hebraico", "id" to "Indonésio", "th" to "Tailandês", "vi" to "Vietnamita")

@Composable
internal fun LanguageSettingsPage(enabledCodes: Set<String>, onLanguage: (String, Boolean) -> Boolean) {
    var lastLanguageMessage by remember { mutableStateOf(false) }
    Heading("Idiomas de tradução")
    Spacer(Modifier.height(14.dp))
    Copy("Escolha quais idiomas aparecem quando você toca em Traduzir.", color = RevisaColors.Muted)
    Spacer(Modifier.height(12.dp))
    Copy("${enabledCodes.size} idiomas selecionados · mantenha pelo menos um.", size = 12.sp)
    if (lastLanguageMessage) Copy("Mantenha pelo menos um idioma selecionado.", size = 12.sp, color = RevisaColors.Green)
    Spacer(Modifier.height(18.dp))
    RevisaCard {
        SupportedLanguages.ALL.forEachIndexed { i, language ->
            if (i > 0) Divider()
            val checked = language.languageCode in enabledCodes
            Row(Modifier.fillMaxWidth().heightIn(min = 53.dp)
                .toggleable(checked, role = Role.Checkbox) {
                    lastLanguageMessage = !onLanguage(language.languageCode, it)
                }.padding(vertical = 15.dp), verticalAlignment = Alignment.CenterVertically) {
                Copy(languageLabels[language.languageCode] ?: language.displayName, Modifier.weight(1f))
                Checkbox(checked, onCheckedChange = null, modifier = Modifier.size(24.dp),
                    colors = CheckboxDefaults.colors(checkedColor = RevisaColors.Blue))
            }
        }
    }
    Spacer(Modifier.height(18.dp))
    Copy("O idioma de destino é escolhido dentro do teclado no momento da tradução.", size = 12.sp, color = RevisaColors.Muted)
    Spacer(Modifier.height(18.dp))
}
