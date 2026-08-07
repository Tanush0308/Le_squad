package com.silentbridge.presentation.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Translate
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.silentbridge.domain.model.ConnectionState
import com.silentbridge.gesture.InferenceState
import com.silentbridge.presentation.viewmodel.MainViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    viewModel: MainViewModel,
    onNavigateToDevices: () -> Unit,
    onNavigateToDiagnostics: () -> Unit,
    onNavigateToStats: () -> Unit,
    onNavigateToManageWords: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()
    var showMenu by remember { mutableStateOf(false) }
    var showLanguageDialog by remember { mutableStateOf(false) }

    val languages = listOf(
        "en" to "English",
        "hi" to "Hindi (हिंदी)",
        "mr" to "Marathi (मराठी)",
        "gu" to "Gujarati (ગુજરાતી)",
        "ta" to "Tamil (தமிழ்)",
        "te" to "Telugu (తెలుగు)",
        "kn" to "Kannada (ಕನ್ನಡ)"
    )

    if (showLanguageDialog) {
        AlertDialog(
            onDismissRequest = { showLanguageDialog = false },
            title = { Text("Select Output Language") },
            text = {
                Column {
                    languages.forEach { (code, name) ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = uiState.targetLanguageCode == code,
                                onClick = {
                                    viewModel.setTargetLanguage(code)
                                    showLanguageDialog = false
                                }
                            )
                            Text(
                                text = name,
                                modifier = Modifier.padding(start = 8.dp),
                                style = MaterialTheme.typography.bodyLarge
                            )
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showLanguageDialog = false }) { Text("Cancel") }
            }
        )
    }
    val context = androidx.compose.ui.platform.LocalContext.current

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("SilentBridge") },
                actions = {
                    IconButton(onClick = { showLanguageDialog = true }) {
                        Icon(Icons.Default.Translate, contentDescription = "Language")
                    }
                    IconButton(onClick = { showMenu = !showMenu }) {
                        Icon(Icons.Default.MoreVert, contentDescription = "More")
                    }
                    DropdownMenu(
                        expanded = showMenu,
                        onDismissRequest = { showMenu = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text("Diagnostics") },
                            onClick = {
                                showMenu = false
                                onNavigateToDiagnostics()
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Prediction Stats") },
                            onClick = {
                                showMenu = false
                                onNavigateToStats()
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Export Dataset (${uiState.feedbackCount})") },
                            onClick = {
                                showMenu = false
                                viewModel.exportDataset()
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Manage Words") },
                            onClick = {
                                showMenu = false
                                onNavigateToManageWords()
                            }
                        )
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Connection Status
            Row(verticalAlignment = Alignment.CenterVertically) {
                val color = when (uiState.connectionState) {
                    ConnectionState.CONNECTED -> Color.Green
                    ConnectionState.CONNECTING -> Color.Yellow
                    ConnectionState.DISCONNECTED -> Color.Red
                    ConnectionState.ERROR -> Color.Red
                    ConnectionState.SEARCHING -> Color.Blue
                }
                Surface(modifier = Modifier.size(10.dp), shape = androidx.compose.foundation.shape.CircleShape, color = color) {}
                Spacer(modifier = Modifier.width(8.dp))
                Text(text = "Glove: ${uiState.connectionState.name}", style = MaterialTheme.typography.labelMedium)
            }

            if (uiState.isDownloadingModel) {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp))
                Text("Downloading language pack...", style = MaterialTheme.typography.labelSmall)
            }

            Spacer(modifier = Modifier.height(16.dp))

            if (uiState.connectionState != ConnectionState.CONNECTED) {
                Button(onClick = onNavigateToDevices, modifier = Modifier.fillMaxWidth()) {
                    Text("Connect Glove")
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Word Buffer
            if (uiState.wordBuffer.isNotEmpty()) {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    itemsIndexed(uiState.wordBuffer) { index, word ->
                        AssistChip(
                            onClick = { viewModel.removeWordFromBuffer(index) },
                            label = { Text(word) },
                            trailingIcon = { Icon(Icons.Default.Close, null, Modifier.size(14.dp)) }
                        )
                    }
                }
            }

            // Gesture Recognition Card
            Card(
                modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Column(Modifier.padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(text = "Gesture: ${uiState.inferenceState.name}", style = MaterialTheme.typography.labelSmall)
                    
                    if (uiState.gestureResult != null) {
                        Text(text = uiState.gestureResult!!.gestureName, style = MaterialTheme.typography.headlineLarge, color = if (uiState.isWrongFlash) Color.Red else Color.Unspecified)
                        
                        if (uiState.showFeedbackButtons && !uiState.showCorrectionSelector) {
                            Row(horizontalArrangement = Arrangement.spacedBy(16.dp), modifier = Modifier.padding(top = 16.dp)) {
                                Button(onClick = { viewModel.onFeedbackYes() }, colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF40a02b))) { Text("YES") }
                                Button(onClick = { viewModel.onFeedbackNo() }, colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFe64553))) { Text("NO") }
                            }
                        }
                    } else {
                        val statusText = when(uiState.inferenceState) {
                            InferenceState.READY -> "Ready - Press START to capture"
                            InferenceState.CALIBRATING -> "Calibrating (Keep Still)..."
                            InferenceState.RECORDING -> "Recording Gesture..."
                            InferenceState.PREPROCESSING, InferenceState.MODEL_INFERENCE -> "Processing AI..."
                            InferenceState.DISCONNECTED -> "Connect Glove first"
                            else -> "Waiting..."
                        }
                        Text(
                            text = statusText,
                            style = MaterialTheme.typography.bodyLarge,
                            color = Color.Gray
                        )
                    }
                }
            }

            // Correction Selector
            if (uiState.showCorrectionSelector) {
                LazyVerticalGrid(columns = GridCells.Fixed(3), modifier = Modifier.height(150.dp)) {
                    items(uiState.modelLabels + uiState.customLabels) { label ->
                        FilterChip(selected = false, onClick = { viewModel.submitCorrectedLabel(label) }, label = { Text(label, fontSize = 10.sp) })
                    }
                }
            }

            Spacer(modifier = Modifier.weight(1f))

            // Sentence Section
            if (uiState.isFormingSentence) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(16.dp)) {
                    CircularProgressIndicator()
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("AI is forming sentence...", style = MaterialTheme.typography.labelMedium, color = Color.Gray)
                }
            } else if (uiState.sentenceError != null) {
                Card(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)) {
                    Column(Modifier.padding(16.dp)) {
                        Text("Error", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onErrorContainer)
                        Text(text = uiState.sentenceError!!, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onErrorContainer)
                        OutlinedButton(onClick = { viewModel.clearSentence(); viewModel.clearBuffer() }, modifier = Modifier.padding(top = 8.dp)) {
                            Text("Clear")
                        }
                    }
                }
            } else if (uiState.formedSentence != null) {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp)) {
                        Text("English:", style = MaterialTheme.typography.labelSmall, color = Color.Gray)
                        Text(text = uiState.formedSentence!!, style = MaterialTheme.typography.bodyLarge)
                        
                        if (uiState.translatedSentence != null && uiState.targetLanguageCode != "en") {
                            HorizontalDivider(Modifier.padding(vertical = 8.dp))
                            Text("Translation:", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                            Text(text = uiState.translatedSentence!!, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                        }

                        Row(Modifier.padding(top = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(onClick = { viewModel.speakSentence(context) }, modifier = Modifier.weight(1f)) {
                                Text("Speak")
                            }
                            OutlinedButton(onClick = { viewModel.clearSentence(); viewModel.clearBuffer() }) {
                                Text("Clear")
                            }
                        }
                    }
                }
            }

            // Controls
            Row(Modifier.padding(top = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (uiState.inferenceState == InferenceState.READY) {
                    Button(
                        onClick = { viewModel.startGestureSession() },
                        enabled = uiState.connectionState == ConnectionState.CONNECTED,
                        modifier = Modifier.weight(1f)
                    ) { Text("START") }
                }
                
                if (viewModel.getTriggerModePublic() == "manual" && uiState.wordBuffer.isNotEmpty()) {
                    Button(onClick = { viewModel.triggerSentenceFormation() }, colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary)) {
                        Text("DONE")
                    }
                }
            }
        }
    }
}
