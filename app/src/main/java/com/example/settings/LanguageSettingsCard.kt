package com.example.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.translation.SupportedLanguages
import com.example.translation.TranslationTargetLanguage

@Composable
fun LanguageSettingsCard() {
    val context = LocalContext.current
    val preferences = remember(context) { LanguagePreferences(context) }
    var enabledCodes by remember { mutableStateOf(preferences.translationLanguages.map { it.languageCode }.toSet()) }
    var showTranslation by remember { mutableStateOf(false) }

    Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Idiomas", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Text("Escolha quais idiomas aparecem ao tocar em Traduzir.",
                style = MaterialTheme.typography.bodyMedium)
            Text("${enabledCodes.size} idiomas selecionados", style = MaterialTheme.typography.bodySmall)
            OutlinedButton(onClick = { showTranslation = true }) { Text("Gerenciar idiomas de tradução") }

        }
    }

    if (showTranslation) {
        AlertDialog(onDismissRequest = { showTranslation = false },
            title = { Text("Idiomas de tradução") },
            text = {
                Column(Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState())) {
                    Text("Marque os idiomas que deseja mostrar no teclado. Mantenha pelo menos um.")
                    SupportedLanguages.ALL.forEach { language ->
                        val selected = language.languageCode in enabledCodes
                        val canChange = !selected || enabledCodes.size > 1
                        val change: () -> Unit = {
                            if (preferences.setTranslationEnabled(language.languageCode, !selected)) {
                                enabledCodes = preferences.translationLanguages.map { it.languageCode }.toSet()
                            }
                        }
                        Row(Modifier.fillMaxWidth().clickable(enabled = canChange, onClick = change)
                            .padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(checked = selected, enabled = canChange, onCheckedChange = { change() })
                            LanguageName(language, Modifier.weight(1f))
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { showTranslation = false }) { Text("Concluído") } })
    }


}

@Composable
private fun LanguageName(language: TranslationTargetLanguage, modifier: Modifier) {
    Column(modifier) {
        Text(language.displayName)
        language.secondaryName?.let { hint ->
            Text(hint, style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
