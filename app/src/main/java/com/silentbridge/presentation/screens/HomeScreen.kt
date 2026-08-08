package com.silentbridge.presentation.screens

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.silentbridge.domain.language.SupportedLanguage
import com.silentbridge.domain.model.ConnectionState
import com.silentbridge.gesture.CaptureMode
import com.silentbridge.gesture.InferenceState
import com.silentbridge.presentation.viewmodel.MainViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    viewModel: MainViewModel,
    onNavigateToDevices: () -> Unit,
    onNavigateToDiagnostics: () -> Unit,
    onNavigateToStats: () -> Unit,
    onNavigateToManageWords: () -> Unit,
    onNavigateToVoiceSettings: () -> Unit,
    onNavigateToTutorials: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()
    var showMenu by remember { mutableStateOf(false) }
    var showLanguageDialog by remember { mutableStateOf(false) }

    val languages = SupportedLanguage.all.map { it.code to "${it.displayName} (${it.nativeName})" }

    if (showLanguageDialog) {
        AlertDialog(
            onDismissRequest = { showLanguageDialog = false },
            title = { Text("Output Language") },
            text = {
                Column {
                    languages.forEach { (code, name) ->
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = uiState.selectedLanguage.code == code,
                                onClick = {
                                    viewModel.setTargetLanguage(code)
                                    showLanguageDialog = false
                                }
                            )
                            Text(text = name, modifier = Modifier.padding(start = 8.dp), style = MaterialTheme.typography.bodyLarge)
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { showLanguageDialog = false }) { Text("Cancel") } },
            shape = RoundedCornerShape(16.dp)
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("SilentBridge", fontWeight = FontWeight.SemiBold) },
                actions = {
                    IconButton(onClick = { showLanguageDialog = true }) {
                        Icon(Icons.Default.Translate, contentDescription = "Language", tint = MaterialTheme.colorScheme.primary)
                    }
                    IconButton(onClick = { showMenu = !showMenu }) {
                        Icon(Icons.Default.MoreVert, contentDescription = "More")
                    }
                    DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
                        DropdownMenuItem(text = { Text("Prediction Stats") }, onClick = { showMenu = false; onNavigateToStats() })
                        DropdownMenuItem(text = { Text("Export Dataset (${uiState.feedbackCount})") }, onClick = { showMenu = false; viewModel.exportDataset() })
                        DropdownMenuItem(text = { Text("Manage Words") }, onClick = { showMenu = false; onNavigateToManageWords() })
                        DropdownMenuItem(text = { Text("Voice & Language") }, onClick = { showMenu = false; onNavigateToVoiceSettings() })
                        DropdownMenuItem(
                            text = { Text("Learn Signs 🤟") },
                            onClick = { showMenu = false; onNavigateToTutorials() }
                        )
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .padding(padding)
                .padding(horizontal = 24.dp, vertical = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {

            // ── MODE SELECTOR ──────────────────────────────────────────────
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                CaptureMode.values().forEach { mode ->
                    val selected = uiState.captureMode == mode
                    val label = if (mode == CaptureMode.AUTO) "Auto" else "Manual"
                    val icon = if (mode == CaptureMode.AUTO) Icons.Default.AutoAwesome else Icons.Default.TouchApp
                    Surface(
                        modifier = Modifier.weight(1f).padding(4.dp),
                        shape = RoundedCornerShape(12.dp),
                        color = if (selected) MaterialTheme.colorScheme.primary else Color.Transparent,
                        onClick = { viewModel.setCaptureMode(mode) }
                    ) {
                        Row(
                            modifier = Modifier.padding(vertical = 10.dp),
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                icon,
                                contentDescription = label,
                                tint = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                label,
                                fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                                color = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                                fontSize = 15.sp
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Mode description chip
            AnimatedContent(targetState = uiState.captureMode, label = "mode_desc") { mode ->
                val desc = if (mode == CaptureMode.AUTO)
                    "Auto: Captures signs continuously. Only high-confidence signs accepted (≥80%)."
                else
                    "Manual: Tap 'Capture Sign' for each gesture. Review each result."
                Text(
                    text = desc,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.outline,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            // ── CONNECTION STATUS BAR ──────────────────────────────────────
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(24.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    val (icon, color, text) = when (uiState.connectionState) {
                        ConnectionState.CONNECTED -> Triple(Icons.Default.BluetoothConnected, Color(0xFF4CAF50), "Connected")
                        ConnectionState.CONNECTING -> Triple(Icons.Default.BluetoothSearching, Color(0xFFFFC107), "Connecting")
                        ConnectionState.DISCONNECTED -> Triple(Icons.Default.BluetoothDisabled, Color.Gray, "Disconnected")
                        ConnectionState.ERROR -> Triple(Icons.Default.BluetoothDisabled, Color(0xFFF44336), "Error")
                        ConnectionState.SEARCHING -> Triple(Icons.Default.BluetoothSearching, Color(0xFF2196F3), "Searching")
                    }
                    Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(20.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(text = text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }

                if (uiState.connectionState != ConnectionState.CONNECTED) {
                    TextButton(onClick = onNavigateToDevices, contentPadding = PaddingValues(0.dp)) {
                        Text("Connect", style = MaterialTheme.typography.labelLarge)
                    }
                } else {
                    val statusText = when (uiState.inferenceState) {
                        InferenceState.READY -> if (uiState.captureMode == CaptureMode.AUTO && uiState.wordBuffer.isNotEmpty()) "Paused" else "Ready"
                        InferenceState.CALIBRATING -> "Calibrating..."
                        InferenceState.RECORDING -> if (uiState.captureMode == CaptureMode.AUTO) "● Capturing" else "Recording"
                        InferenceState.PREPROCESSING, InferenceState.MODEL_INFERENCE -> "Thinking..."
                        else -> ""
                    }
                    Text(
                        statusText,
                        style = MaterialTheme.typography.labelMedium,
                        color = if (uiState.inferenceState == InferenceState.RECORDING && uiState.captureMode == CaptureMode.AUTO)
                            Color(0xFF4CAF50) else MaterialTheme.colorScheme.primary
                    )
                }
            }

            if (uiState.isDownloadingModel) {
                Spacer(modifier = Modifier.height(12.dp))
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(4.dp)))
                Text("Downloading language models...", style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(top = 4.dp))
            }

            Spacer(modifier = Modifier.height(12.dp))

            // ── MAIN GESTURE DISPLAY ───────────────────────────────────────
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .clip(RoundedCornerShape(32.dp))
                    .background(MaterialTheme.colorScheme.surface),
                contentAlignment = Alignment.Center
            ) {
                if (uiState.captureMode == CaptureMode.AUTO) {
                    // AUTO mode: show word count counter in center
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        if (uiState.inferenceState == InferenceState.RECORDING) {
                            Text("Capturing...", style = MaterialTheme.typography.titleMedium, color = Color(0xFF4CAF50))
                            Spacer(modifier = Modifier.height(8.dp))
                        }
                        Text(
                            text = "${uiState.wordBuffer.size}",
                            style = MaterialTheme.typography.displayLarge,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = if (uiState.wordBuffer.size == 1) "sign captured" else "signs captured",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                        )
                    }
                } else {
                    // MANUAL mode: show recognised gesture
                    if (uiState.gestureResult != null) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = uiState.gestureResult!!.gestureName,
                                style = MaterialTheme.typography.displayMedium,
                                fontWeight = FontWeight.Bold,
                                color = if (uiState.isWrongFlash) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface
                            )
                            if (uiState.showFeedbackButtons && !uiState.showCorrectionSelector) {
                                Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.padding(top = 12.dp)) {
                                    FilledIconButton(
                                        onClick = { viewModel.onFeedbackYes() },
                                        colors = IconButtonDefaults.filledIconButtonColors(
                                            containerColor = Color(0xFF4CAF50).copy(alpha = 0.2f),
                                            contentColor = Color(0xFF4CAF50)
                                        )
                                    ) { Icon(Icons.Default.Check, contentDescription = "Correct") }
                                    FilledIconButton(
                                        onClick = { viewModel.onFeedbackNo() },
                                        colors = IconButtonDefaults.filledIconButtonColors(
                                            containerColor = MaterialTheme.colorScheme.errorContainer,
                                            contentColor = MaterialTheme.colorScheme.onErrorContainer
                                        )
                                    ) { Icon(Icons.Default.Close, contentDescription = "Incorrect") }
                                }
                            }
                        }
                    } else {
                        Icon(
                            imageVector = Icons.Default.Bluetooth,
                            contentDescription = null,
                            modifier = Modifier.size(64.dp),
                            tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f)
                        )
                    }
                }
            }

            // Correction selector (Manual mode only)
            AnimatedVisibility(visible = uiState.showCorrectionSelector && uiState.captureMode == CaptureMode.MANUAL) {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(3),
                    modifier = Modifier.fillMaxWidth().height(120.dp).padding(vertical = 12.dp)
                ) {
                    items(uiState.modelLabels + uiState.customLabels) { label ->
                        Surface(
                            shape = RoundedCornerShape(16.dp),
                            color = MaterialTheme.colorScheme.secondaryContainer,
                            modifier = Modifier.padding(4.dp).clip(RoundedCornerShape(16.dp)),
                            onClick = { viewModel.submitCorrectedLabel(label) }
                        ) {
                            Text(label, fontSize = 12.sp, textAlign = TextAlign.Center, modifier = Modifier.padding(8.dp))
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // ── WORD BUFFER ────────────────────────────────────────────────
            AnimatedVisibility(visible = uiState.wordBuffer.isNotEmpty()) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Text("Current Sentence", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.outline)
                    Spacer(modifier = Modifier.height(8.dp))
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                        itemsIndexed(uiState.wordBuffer) { index, word ->
                            InputChip(
                                selected = false,
                                onClick = { viewModel.removeWordFromBuffer(index) },
                                label = { Text(word, fontWeight = FontWeight.Medium) },
                                trailingIcon = { Icon(Icons.Default.Close, null, Modifier.size(16.dp)) },
                                shape = RoundedCornerShape(16.dp),
                                colors = InputChipDefaults.inputChipColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // ── FINAL OUTPUT CARD ──────────────────────────────────────────
            AnimatedVisibility(visible = uiState.formedSentence != null) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(24.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f))
                ) {
                    Column(Modifier.padding(20.dp)) {
                        Text(
                            text = uiState.formedSentence ?: "",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                        if (uiState.translatedSentence != null && uiState.selectedLanguage != SupportedLanguage.English) {
                            Spacer(Modifier.height(12.dp))
                            Text(
                                text = uiState.translatedSentence!!,
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                        Row(
                            Modifier.fillMaxWidth().padding(top = 16.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            TextButton(onClick = { viewModel.clearSentence(); viewModel.clearBuffer() }) {
                                Text("Clear All", color = MaterialTheme.colorScheme.error)
                            }
                            FilledTonalButton(onClick = {
                                val toSpeak = uiState.translatedSentence ?: uiState.formedSentence!!
                                viewModel.speakSentence(toSpeak)
                            }) {
                                Icon(Icons.Default.PlayArrow, contentDescription = "Speak", modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(8.dp))
                                Text("Speak Again")
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // ── ACTION BUTTONS ─────────────────────────────────────────────
            if (uiState.captureMode == CaptureMode.AUTO) {
                // AUTO mode: Start / Stop + Done
                Row(Modifier.fillMaxWidth().padding(bottom = 16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    val isCapturing = uiState.inferenceState == InferenceState.RECORDING ||
                                      uiState.inferenceState == InferenceState.PREPROCESSING ||
                                      uiState.inferenceState == InferenceState.MODEL_INFERENCE

                    if (!isCapturing) {
                        OutlinedButton(
                            onClick = { viewModel.startGestureCapture() },
                            enabled = uiState.connectionState == ConnectionState.CONNECTED && uiState.formedSentence == null,
                            modifier = Modifier.weight(1f).height(56.dp),
                            shape = RoundedCornerShape(16.dp)
                        ) {
                            Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(20.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Start Auto", fontSize = 15.sp)
                        }
                    } else {
                        OutlinedButton(
                            onClick = { viewModel.stopAutoCapture() },
                            modifier = Modifier.weight(1f).height(56.dp),
                            shape = RoundedCornerShape(16.dp),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error)
                        ) {
                            Icon(Icons.Default.Stop, contentDescription = null, modifier = Modifier.size(20.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Stop", fontSize = 15.sp)
                        }
                    }

                    if (uiState.wordBuffer.isNotEmpty() && uiState.formedSentence == null) {
                        Button(
                            onClick = {
                                viewModel.stopAutoCapture()
                                viewModel.triggerSentenceFormation()
                            },
                            modifier = Modifier.weight(1f).height(56.dp),
                            shape = RoundedCornerShape(16.dp)
                        ) {
                            Icon(Icons.Rounded.CheckCircle, contentDescription = null, modifier = Modifier.size(20.dp))
                            Spacer(Modifier.width(6.dp))
                            Text(if (uiState.isFormingSentence) "Translating..." else "Done", fontSize = 15.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            } else {
                // MANUAL mode: Capture Sign + Done
                Row(Modifier.fillMaxWidth().padding(bottom = 16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    val isReady = uiState.connectionState == ConnectionState.CONNECTED && uiState.inferenceState == InferenceState.READY
                    val isProcessing = uiState.inferenceState != InferenceState.READY && uiState.inferenceState != InferenceState.DISCONNECTED

                    if (isProcessing) {
                        Button(
                            onClick = { viewModel.stopGestureSession() },
                            modifier = Modifier.weight(1f).height(56.dp),
                            shape = RoundedCornerShape(16.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                        ) { Text("Stop", fontSize = 15.sp) }
                    } else {
                        OutlinedButton(
                            onClick = { viewModel.startGestureSession() },
                            enabled = isReady && uiState.formedSentence == null,
                            modifier = Modifier.weight(1f).height(56.dp),
                            shape = RoundedCornerShape(16.dp)
                        ) { Text("Capture Sign", fontSize = 15.sp) }
                    }

                    if (uiState.wordBuffer.isNotEmpty() && uiState.formedSentence == null) {
                        Button(
                            onClick = { viewModel.triggerSentenceFormation() },
                            modifier = Modifier.weight(1f).height(56.dp),
                            shape = RoundedCornerShape(16.dp)
                        ) {
                            Icon(Icons.Rounded.CheckCircle, contentDescription = null, modifier = Modifier.size(20.dp))
                            Spacer(Modifier.width(6.dp))
                            Text(if (uiState.isFormingSentence) "Translating..." else "Done", fontSize = 15.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }
}
