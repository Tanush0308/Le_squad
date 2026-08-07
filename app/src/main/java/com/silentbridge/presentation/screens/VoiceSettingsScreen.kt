package com.silentbridge.presentation.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.RecordVoiceOver
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.silentbridge.domain.language.SupportedLanguage
import com.silentbridge.domain.speech.TtsStatus
import com.silentbridge.domain.translation.ModelStatus
import com.silentbridge.presentation.viewmodel.SettingsViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VoiceSettingsScreen(
    viewModel: SettingsViewModel,
    onNavigateBack: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Voice & Language Settings") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // ─── TTS Status ───────────────────────────────────────────────
            item {
                Spacer(Modifier.height(8.dp))
                Text(
                    "Text-to-Speech Status",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(Modifier.height(4.dp))
                TtsStatusCard(uiState.ttsStatus)
                Spacer(Modifier.height(16.dp))
            }

            // ─── Language Selection ───────────────────────────────────────
            item {
                Text(
                    "Output Language",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(Modifier.height(4.dp))
            }

            items(uiState.languages) { language ->
                LanguageRow(
                    language = language,
                    isSelected = uiState.selectedLanguage == language,
                    modelStatus = if (language == SupportedLanguage.English) null
                                  else uiState.modelStatuses[language.code],
                    onSelect = { viewModel.selectLanguage(language) },
                    onDownload = { viewModel.downloadModel(language) }
                )
            }

            // ─── Test Voice ───────────────────────────────────────────────
            item {
                Spacer(Modifier.height(16.dp))
                Text(
                    "Voice Preview",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    "Plays \"I need water.\" in the selected language.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(8.dp))
                Button(
                    onClick = { viewModel.testVoice() },
                    enabled = !uiState.isTesting,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.PlayArrow, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text(if (uiState.isTesting) "Playing…" else "Test Voice")
                }
                Spacer(Modifier.height(24.dp))
            }
        }
    }
}

@Composable
private fun TtsStatusCard(status: TtsStatus) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = when (status) {
                is TtsStatus.Available -> MaterialTheme.colorScheme.primaryContainer
                TtsStatus.MissingData -> MaterialTheme.colorScheme.errorContainer
                TtsStatus.NotSupported -> MaterialTheme.colorScheme.errorContainer
                TtsStatus.Initializing -> MaterialTheme.colorScheme.surfaceVariant
                TtsStatus.Error -> MaterialTheme.colorScheme.errorContainer
            }
        )
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Icon(
                Icons.Default.RecordVoiceOver,
                contentDescription = null,
                tint = when (status) {
                    is TtsStatus.Available -> MaterialTheme.colorScheme.primary
                    else -> MaterialTheme.colorScheme.error
                }
            )
            Column {
                Text(
                    text = when (status) {
                        is TtsStatus.Available -> "Supported"
                        TtsStatus.MissingData -> "Missing Voice Data"
                        TtsStatus.NotSupported -> "Language Not Supported"
                        TtsStatus.Initializing -> "Initializing…"
                        TtsStatus.Error -> "TTS Error"
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold
                )
                if (status is TtsStatus.Available) {
                    Text(
                        text = "Voice: ${status.voiceName}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
            }
        }
    }
}

@Composable
private fun LanguageRow(
    language: SupportedLanguage,
    isSelected: Boolean,
    modelStatus: ModelStatus?,
    onSelect: () -> Unit,
    onDownload: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) MaterialTheme.colorScheme.primaryContainer
                            else MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            RadioButton(selected = isSelected, onClick = onSelect)

            Column(modifier = Modifier.weight(1f).padding(start = 8.dp)) {
                Text(
                    text = language.displayName,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal
                )
                Text(
                    text = language.nativeName,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // Model status badge (English has no model to download)
            if (modelStatus != null) {
                ModelStatusBadge(status = modelStatus, onDownload = onDownload)
            }
        }
    }
}

@Composable
private fun ModelStatusBadge(status: ModelStatus, onDownload: () -> Unit) {
    when (status) {
        ModelStatus.Downloaded -> {
            Icon(
                Icons.Default.Check,
                contentDescription = "Downloaded",
                tint = Color(0xFF2ECC71),
                modifier = Modifier.size(20.dp)
            )
        }
        ModelStatus.Downloading -> {
            CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
        }
        ModelStatus.NotInstalled -> {
            IconButton(onClick = onDownload, modifier = Modifier.size(36.dp)) {
                Icon(
                    Icons.Default.Download,
                    contentDescription = "Download model",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
        is ModelStatus.Failed -> {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("Failed", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.error)
                TextButton(onClick = onDownload, contentPadding = PaddingValues(0.dp)) {
                    Text("Retry", style = MaterialTheme.typography.labelSmall)
                }
            }
        }
    }
}
