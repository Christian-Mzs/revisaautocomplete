package com.example.settings

import android.content.Context
import android.content.Intent
import android.provider.Settings
import android.view.inputmethod.InputMethodManager
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Keyboard
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.privacy.TranslationConsentManager

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    isImeEnabled: Boolean,
    isImeSelected: Boolean,
    onRefreshImeStatus: () -> Unit
) {
    val context = LocalContext.current
    val consentManager = remember { TranslationConsentManager.getInstance(context) }
    var showSettingsDialog by remember { mutableStateOf(false) }
    var isDiagnosticMode by remember { mutableStateOf(consentManager.isDiagnosticModeEnabled()) }

    val isReady = isImeEnabled && isImeSelected

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.AutoAwesome,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Teclado Corretor",
                            fontWeight = FontWeight.Bold,
                            fontSize = 20.sp
                        )
                    }
                },
                actions = {
                    IconButton(
                        onClick = { showSettingsDialog = true },
                        modifier = Modifier.testTag("btn_open_settings_dialog")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Settings,
                            contentDescription = "Configurações"
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {

            // Header Intro
            Column(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = "✨ Teclado Corretor",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Corrija suas frases diretamente enquanto digita em qualquer aplicativo.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // Step 1: Ativar Teclado Corretor
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("step_1_card"),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = if (isImeEnabled) Color(0xFF10B981).copy(alpha = 0.08f)
                    else MaterialTheme.colorScheme.surfaceVariant
                )
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(
                                if (isImeEnabled) Color(0xFF10B981)
                                else MaterialTheme.colorScheme.primary
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        if (isImeEnabled) {
                            Icon(
                                imageVector = Icons.Default.Check,
                                contentDescription = "Ativado",
                                tint = Color.White,
                                modifier = Modifier.size(20.dp)
                            )
                        } else {
                            Text(
                                text = "1",
                                color = Color.White,
                                fontWeight = FontWeight.Bold,
                                fontSize = 16.sp
                            )
                        }
                    }

                    Spacer(modifier = Modifier.width(14.dp))

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Passo 1",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = if (isImeEnabled) "✓ Teclado ativado" else "Ativar Teclado Corretor",
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 16.sp,
                            color = if (isImeEnabled) Color(0xFF059669) else MaterialTheme.colorScheme.onSurface
                        )
                        if (!isImeEnabled) {
                            Text(
                                text = "Habilitar nas opções de entrada do Android",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    if (!isImeEnabled) {
                        Button(
                            onClick = {
                                val intent = Intent(Settings.ACTION_INPUT_METHOD_SETTINGS).apply {
                                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                                }
                                context.startActivity(intent)
                            },
                            modifier = Modifier.testTag("btn_activate_ime"),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Text("Ativar teclado")
                        }
                    }
                }
            }

            // Step 2: Selecionar Teclado Corretor
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("step_2_card"),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = if (isImeSelected) Color(0xFF10B981).copy(alpha = 0.08f)
                    else MaterialTheme.colorScheme.surfaceVariant
                )
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(
                                if (isImeSelected) Color(0xFF10B981)
                                else MaterialTheme.colorScheme.primary
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        if (isImeSelected) {
                            Icon(
                                imageVector = Icons.Default.Check,
                                contentDescription = "Selecionado",
                                tint = Color.White,
                                modifier = Modifier.size(20.dp)
                            )
                        } else {
                            Text(
                                text = "2",
                                color = Color.White,
                                fontWeight = FontWeight.Bold,
                                fontSize = 16.sp
                            )
                        }
                    }

                    Spacer(modifier = Modifier.width(14.dp))

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Passo 2",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = if (isImeSelected) "✓ Teclado selecionado" else "Selecionar Teclado Corretor",
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 16.sp,
                            color = if (isImeSelected) Color(0xFF059669) else MaterialTheme.colorScheme.onSurface
                        )
                        if (!isImeSelected) {
                            Text(
                                text = "Definir como método de entrada atual",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    if (!isImeSelected) {
                        Button(
                            onClick = {
                                val imm = context.getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
                                imm?.showInputMethodPicker()
                            },
                            enabled = isImeEnabled,
                            modifier = Modifier.testTag("btn_select_ime"),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Text("Escolher teclado")
                        }
                    }
                }
            }

            // Status Card
            AnimatedVisibility(visible = isReady) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("status_ready_card"),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = Color(0xFF10B981).copy(alpha = 0.15f)
                    )
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(18.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.CheckCircle,
                            contentDescription = null,
                            tint = Color(0xFF10B981),
                            modifier = Modifier.size(32.dp)
                        )
                        Spacer(modifier = Modifier.width(14.dp))
                        Column {
                            Text(
                                text = "✓ Pronto para usar",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF047857)
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = "Abra qualquer aplicativo e comece a digitar.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = Color(0xFF065F46)
                            )
                        }
                    }
                }
            }

            // Privacy Card
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalAlignment = Alignment.Top
                ) {
                    Icon(
                        imageVector = Icons.Default.Security,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(22.dp)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(
                            text = "Privacidade",
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = "O texto só é enviado ao serviço de tradução quando você toca em ✨ Corrigir. Campos de senha são protegidos e nunca lidos.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            lineHeight = 18.sp
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))
        }
    }

    // Simple settings dialog
    if (showSettingsDialog) {
        AlertDialog(
            onDismissRequest = { showSettingsDialog = false },
            title = {
                Text(text = "Configurações", fontWeight = FontWeight.Bold)
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Modo de diagnóstico",
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 14.sp
                            )
                            Text(
                                text = "Registra tempos e status de requisições no Logcat",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = isDiagnosticMode,
                            onCheckedChange = { checked ->
                                isDiagnosticMode = checked
                                consentManager.setDiagnosticModeEnabled(checked)
                            },
                            modifier = Modifier.testTag("switch_diagnostic_dialog")
                        )
                    }

                    HorizontalDivider()

                    Text(
                        text = "Versão 1.0 — Teclado com correção rápida via tradução em duas etapas (PT → EN → PT).",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = { showSettingsDialog = false }) {
                    Text("Fechar")
                }
            }
        )
    }
}
