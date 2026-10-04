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
import com.example.suggestions.KeyboardLanguage

@Composable
internal fun TypingLanguageSettingsPage(
    activeCodes: Set<String>,
    onLanguage: (String, Boolean) -> Boolean
) {
    var keepOneMessage by remember { mutableStateOf(false) }
    Heading("Idiomas do teclado")
    Spacer(Modifier.height(14.dp))
    Copy("Escolha os idiomas disponíveis para alternar no teclado. A ordem em que são ativados define a alternância.",
        color = RevisaColors.Muted)
    Spacer(Modifier.height(12.dp))
    Copy("${activeCodes.size} idiomas ativos", size = 12.sp)
    if (keepOneMessage) Copy("Mantenha pelo menos um idioma ativo.", size = 12.sp, color = RevisaColors.Green)
    Spacer(Modifier.height(18.dp))
    RevisaCard {
        KeyboardLanguage.AVAILABLE.forEachIndexed { index, language ->
            if (index > 0) Divider()
            val checked = language.code in activeCodes
            Row(Modifier.fillMaxWidth().heightIn(min = 53.dp)
                .toggleable(checked, role = Role.Checkbox) { keepOneMessage = !onLanguage(language.code, it) }
                .padding(vertical = 15.dp), verticalAlignment = Alignment.CenterVertically) {
                Copy(language.label, Modifier.weight(1f))
                Checkbox(checked, onCheckedChange = null, modifier = Modifier.size(24.dp),
                    colors = CheckboxDefaults.colors(checkedColor = RevisaColors.Blue))
            }
        }
    }
    Spacer(Modifier.height(18.dp))
    Copy("O idioma inicial é detectado do sistema na primeira utilização. Depois, esta lista é controlada pelo Revisa.",
        size = 12.sp, color = RevisaColors.Muted)
    Spacer(Modifier.height(18.dp))
}
