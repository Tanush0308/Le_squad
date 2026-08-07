package com.silentbridge.presentation.viewmodel

import android.app.Application
import android.content.Context
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.silentbridge.data.feedback.FeedbackExporter
import com.silentbridge.data.feedback.FeedbackLogger
import com.silentbridge.data.repository.FeedbackRepository
import com.silentbridge.domain.language.SupportedLanguage
import com.silentbridge.domain.model.BluetoothDeviceDomain
import com.silentbridge.domain.model.ConnectionState
import com.silentbridge.domain.model.GestureResult
import com.silentbridge.domain.model.SensorFrame
import com.silentbridge.domain.speech.TtsStatus
import com.silentbridge.domain.usecase.ChangeLanguageUseCase
import com.silentbridge.domain.usecase.ConnectDeviceUseCase
import com.silentbridge.domain.usecase.DisconnectDeviceUseCase
import com.silentbridge.domain.usecase.GetPairedDevicesUseCase
import com.silentbridge.domain.usecase.LanguageEngineUseCase
import com.silentbridge.domain.usecase.ObserveConnectionStateUseCase
import com.silentbridge.domain.usecase.ObserveSensorDataUseCase
import com.silentbridge.domain.usecase.SpeakSentenceUseCase
import com.silentbridge.domain.usecase.TranslateSentenceUseCase
import com.silentbridge.gesture.GestureEngine
import com.silentbridge.gesture.InferenceState
import com.silentbridge.domain.repository.LanguageRepository
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import org.json.JSONArray
import org.json.JSONObject

data class MainUiState(
    val connectionState: ConnectionState = ConnectionState.DISCONNECTED,
    val latestSensorFrame: SensorFrame? = null,
    val availableDevices: List<BluetoothDeviceDomain> = emptyList(),
    val errorMessage: String? = null,
    val inferenceState: InferenceState = InferenceState.DISCONNECTED,
    val gestureResult: GestureResult? = null,
    val topPredictions: List<GestureResult> = emptyList(),
    val showFeedbackButtons: Boolean = false,
    val isWrongFlash: Boolean = false,
    val showCorrectionSelector: Boolean = false,
    val feedbackSubmitted: Boolean = false,
    val feedbackCount: Int = 0,
    val wordBuffer: List<String> = emptyList(),
    val formedSentence: String? = null,
    val translatedSentence: String? = null,
    val isFormingSentence: Boolean = false,
    val sentenceError: String? = null,
    val alternativesFor: Pair<Int, List<String>>? = null,
    val modelLabels: List<String> = emptyList(),
    val customLabels: List<String> = emptyList(),
    // Language is now typed — SupportedLanguage instead of a raw string code
    val selectedLanguage: SupportedLanguage = SupportedLanguage.English,
    val ttsStatus: TtsStatus = TtsStatus.Initializing,
    val isDownloadingModel: Boolean = false
)

class MainViewModel(
    private val application: Application,
    private val getPairedDevicesUseCase: GetPairedDevicesUseCase,
    private val connectDeviceUseCase: ConnectDeviceUseCase,
    private val disconnectDeviceUseCase: DisconnectDeviceUseCase,
    private val observeSensorDataUseCase: ObserveSensorDataUseCase,
    private val observeConnectionStateUseCase: ObserveConnectionStateUseCase,
    private val gestureEngine: GestureEngine,
    private val feedbackRepository: FeedbackRepository,
    private val feedbackLogger: FeedbackLogger,
    private val feedbackExporter: FeedbackExporter,
    private val languageEngineUseCase: LanguageEngineUseCase,
    private val languageRepository: LanguageRepository,
    private val translateSentenceUseCase: TranslateSentenceUseCase,
    private val speakSentenceUseCase: SpeakSentenceUseCase,
    private val changeLanguageUseCase: ChangeLanguageUseCase
) : ViewModel() {

    private val prefs = application.getSharedPreferences("silentbridge_feedback", Context.MODE_PRIVATE)

    // No cloud API keys — inference is fully offline via LanguageEngine
    private val KEY_CUSTOM_WORDS = "custom_words"
    private val KEY_TARGET_LANG = "target_lang"

    private val _uiState = MutableStateFlow(MainUiState())
    val uiState: StateFlow<MainUiState> = _uiState.asStateFlow()

    private val _wordBuffer = MutableStateFlow<List<String>>(emptyList())
    val wordBuffer: StateFlow<List<String>> = _wordBuffer.asStateFlow()

    private val MAX_BUFFER_SIZE = 25
    private var pauseTimerJob: Job? = null
    private val PAUSE_DURATION_MS = 5000L

    init {
        observeSelectedLanguage()
        loadLabels()
        observeConnection()
        observeSensorData()
        observeInferenceState()
        observeGestureResult()
        observeTopPredictions()
        observeWordBuffer()
        refreshDevices()
        updateFeedbackCount()
    }

    private fun observeSelectedLanguage() {
        viewModelScope.launch {
            languageRepository.selectedLanguage.collect { lang ->
                _uiState.update { it.copy(selectedLanguage = lang) }
            }
        }
    }

    fun setTargetLanguage(language: SupportedLanguage) {
        changeLanguageUseCase(language)
        // Re-translate current sentence if one exists
        _uiState.value.formedSentence?.let { sentence ->
            viewModelScope.launch { translateAndAutoSpeak(sentence) }
        }
    }

    // Convenience overload for HomeScreen language dialog (still accepts code strings)
    fun setTargetLanguage(langCode: String) = setTargetLanguage(SupportedLanguage.fromCode(langCode))

    /**
     * Translates [text] into the currently selected language, updates UI state,
     * then auto-speaks the translated result (per user preference).
     */
    private fun translateSentence(text: String) {
        viewModelScope.launch { translateAndAutoSpeak(text) }
    }

    private suspend fun translateAndAutoSpeak(text: String) {
        val lang = _uiState.value.selectedLanguage
        val translated = if (lang == SupportedLanguage.English) text
                         else translateSentenceUseCase(text, lang)
        _uiState.update { it.copy(translatedSentence = translated) }
        // Auto-speak the translated result immediately
        speakSentenceUseCase(translated, lang)
    }



    fun loadLabels() {
        viewModelScope.launch {
            val modelLabels = mutableListOf<String>()
            try {
                val jsonString = application.assets.open("label_map.json").bufferedReader().use { it.readText() }
                val jsonObject = Json { ignoreUnknownKeys = true }.parseToJsonElement(jsonString).jsonObject
                jsonObject.keys.forEach { modelLabels.add(it) }
            } catch (e: Exception) {
                Log.e("MainViewModel", "Error loading labels for UI: ${e.message}")
            }

            _uiState.update { it.copy(
                modelLabels = modelLabels.sorted(),
                customLabels = loadCustomWords()
            ) }
        }
    }

    fun loadCustomWords(): List<String> {
        val raw = prefs.getString(KEY_CUSTOM_WORDS, "") ?: ""
        return if (raw.isBlank()) emptyList() else raw.split(",").map { it.trim().uppercase() }
    }

    fun addCustomWord(word: String) {
        val current = loadCustomWords().toMutableList()
        val clean = word.trim().uppercase()
        if (clean.isNotBlank() && clean !in current) {
            current.add(clean)
            prefs.edit().putString(KEY_CUSTOM_WORDS, current.joinToString(",")).apply()
            _uiState.update { it.copy(customLabels = current) }
        }
    }

    fun removeCustomWord(word: String) {
        val current = loadCustomWords().toMutableList()
        current.remove(word.uppercase())
        prefs.edit().putString(KEY_CUSTOM_WORDS, current.joinToString(",")).apply()
        _uiState.update { it.copy(customLabels = current) }
    }

    fun refreshDevices() {
        val devices = getPairedDevicesUseCase()
        _uiState.update { it.copy(availableDevices = devices) }
    }

    fun connectDevice(address: String) {
        viewModelScope.launch {
            val success = connectDeviceUseCase(address)
            if (!success) {
                _uiState.update { it.copy(errorMessage = "Connection failed") }
            }
        }
    }

    fun disconnectDevice() {
        disconnectDeviceUseCase()
    }

    fun startGestureSession() {
        _uiState.update { it.copy(feedbackSubmitted = false, showCorrectionSelector = false) }
        gestureEngine.startSession()
    }

    fun stopGestureSession() {
        gestureEngine.stopSession()
    }

    fun resetGestureEngine() {
        gestureEngine.resetEngine()
        _uiState.update { it.copy(feedbackSubmitted = false, showCorrectionSelector = false) }
    }

    fun addWordToBuffer(word: String) {
        val current = _wordBuffer.value.toMutableList()
        current.add(word)
        _wordBuffer.value = current
        Log.d("SilentBridge", "Word added: $word | Buffer: $current")
        if (current.size >= MAX_BUFFER_SIZE) {
            triggerSentenceFormation()
            return
        }
        if (getTriggerMode() == "auto") {
            resetPauseTimer()
        }
    }

    fun removeWordFromBuffer(index: Int) {
        val current = _wordBuffer.value.toMutableList()
        if (index in current.indices) {
            current.removeAt(index)
            _wordBuffer.value = current
        }
    }

    fun clearBuffer() {
        _wordBuffer.value = emptyList()
        cancelPauseTimer()
    }

    private fun getTriggerMode(): String =
        prefs.getString("trigger_mode", "manual") ?: "manual"

    fun getTriggerModePublic(): String = getTriggerMode()

    fun saveTriggerMode(mode: String) {
        prefs.edit().putString("trigger_mode", mode).apply()
    }

    private fun resetPauseTimer() {
        cancelPauseTimer()
        pauseTimerJob = viewModelScope.launch {
            delay(PAUSE_DURATION_MS)
            if (_wordBuffer.value.isNotEmpty()) {
                triggerSentenceFormation()
            }
        }
    }

    private fun cancelPauseTimer() {
        pauseTimerJob?.cancel()
        pauseTimerJob = null
    }

    fun triggerSentenceFormation() {
        val words = _wordBuffer.value
        if (words.isEmpty()) return
        cancelPauseTimer()
        _uiState.update { it.copy(isFormingSentence = true, formedSentence = null, translatedSentence = null, sentenceError = null) }
        viewModelScope.launch {
            try {
                val result = callGeminiNano(words)
                
                _uiState.update { it.copy(isFormingSentence = false) }
                
                if (result != null) {
                    _uiState.update { it.copy(formedSentence = result) }
                    translateSentence(result)
                } else {
                    val fallback = words.joinToString(" ")
                        .lowercase().replaceFirstChar { it.uppercase() } + "."
                    _uiState.update { it.copy(
                        formedSentence = fallback,
                        sentenceError = "AI unavailable (tried all models)"
                    ) }
                    translateSentence(fallback)
                }
            } catch (t: Throwable) {
                Log.e("SilentBridge", "Sentence formation error", t)
                val fallback = words.joinToString(" ")
                    .lowercase().replaceFirstChar { it.uppercase() } + "."
                _uiState.update { it.copy(
                    isFormingSentence = false,
                    formedSentence = fallback,
                    sentenceError = "Error: ${t.message ?: "Unknown error"}"
                ) }
            }
        }
    }

    private suspend fun callGeminiNano(words: List<String>): String? {
        val result = languageEngineUseCase.reconstructSentence(words)
        return result.getOrNull()
    }

    fun fetchAlternativesForWord(wordIndex: Int, word: String) {
        val sentence = _uiState.value.formedSentence ?: return
        viewModelScope.launch {
            val alts = callGeminiNanoForAlternatives(word, sentence)
            _uiState.update { it.copy(alternativesFor = Pair(wordIndex, alts)) }
        }
    }

    private suspend fun callGeminiNanoForAlternatives(word: String, sentence: String): List<String> {
        return try {
            val systemPrompt = "You are a helpful assistant suggesting alternative words."
            val userPrompt = "Sentence: \"$sentence\"\nSuggest exactly 3 alternative single words for \"$word\".\nReply with ONLY the 3 words separated by commas."
            val prompt = """
                <|im_start|>system
                $systemPrompt<|im_end|>
                <|im_start|>user
                $userPrompt<|im_end|>
                <|im_start|>assistant
                
            """.trimIndent()

            val result = languageEngineUseCase.generateDirect(prompt)
            val text = result.getOrNull()

            text?.split(",")
                ?.map { it.trim() }
                ?.filter { it.isNotBlank() }
                ?.take(3)
                ?: emptyList()
        } catch (e: Exception) {
            emptyList()
        }
    }

    fun replaceWordInSentence(wordIndex: Int, newWord: String) {
        val sentence = _uiState.value.formedSentence ?: return
        val words = sentence.trimEnd('.').split(" ").toMutableList()
        if (wordIndex in words.indices) {
            words[wordIndex] = newWord
            viewModelScope.launch {
                val smoothed = callGeminiNano(words)
                if (smoothed != null) {
                    _uiState.update { it.copy(formedSentence = smoothed, alternativesFor = null) }
                    translateSentence(smoothed)
                } else {
                    val fallback = words.joinToString(" ") + "."
                    _uiState.update { it.copy(formedSentence = fallback, alternativesFor = null) }
                    translateSentence(fallback)
                }
            }
        } else {
            _uiState.update { it.copy(alternativesFor = null) }
        }
    }

    fun dismissAlternatives() {
        _uiState.update { it.copy(alternativesFor = null) }
    }

    fun clearSentence() {
        _uiState.update { it.copy(formedSentence = null, translatedSentence = null, sentenceError = null, alternativesFor = null) }
    }

    /** Speak the given sentence in the currently selected language. */
    fun speakSentence(sentence: String) {
        val lang = _uiState.value.selectedLanguage
        viewModelScope.launch {
            speakSentenceUseCase(sentence, lang)
        }
    }

    fun onFeedbackYes() {
        if (_uiState.value.feedbackSubmitted) return
        val result = _uiState.value.gestureResult ?: return
        
        addWordToBuffer(result.gestureName)

        viewModelScope.launch {
            _uiState.update { it.copy(feedbackSubmitted = true) }
            feedbackRepository.recordFeedback(result.gestureName, isCorrect = true)
            feedbackLogger.saveFeedback(result, confirmed = true, correctLabel = result.gestureName)
            updateFeedbackCount()
            delay(300)
            _uiState.update { it.copy(showFeedbackButtons = false) }
            gestureEngine.resetEngine()
        }
    }

    fun onFeedbackNo() {
        if (_uiState.value.feedbackSubmitted) return
        _uiState.update { it.copy(showCorrectionSelector = true) }
    }

    fun submitCorrectedLabel(label: String) {
        val result = _uiState.value.gestureResult ?: return
        
        viewModelScope.launch {
            _uiState.update { it.copy(feedbackSubmitted = true, showCorrectionSelector = false) }
            feedbackRepository.recordFeedback(result.gestureName, isCorrect = false)
            feedbackLogger.saveFeedback(result, confirmed = false, correctLabel = label)
            updateFeedbackCount()
            _uiState.update { it.copy(isWrongFlash = true) }
            delay(600)
            _uiState.update { it.copy(isWrongFlash = false, showFeedbackButtons = false) }
            gestureEngine.resetEngine()
        }
    }

    fun exportDataset() {
        feedbackExporter.exportDataset()
    }

    private fun updateFeedbackCount() {
        viewModelScope.launch {
            val count = feedbackLogger.getFeedbackCount()
            _uiState.update { it.copy(feedbackCount = count) }
        }
    }

    private fun observeConnection() {
        viewModelScope.launch {
            observeConnectionStateUseCase().collect { state ->
                _uiState.update { it.copy(connectionState = state) }
                if (state == ConnectionState.CONNECTED) {
                    gestureEngine.onBluetoothConnected()
                } else {
                    gestureEngine.onBluetoothDisconnected()
                }
            }
        }
    }

    private fun observeSensorData() {
        viewModelScope.launch {
            observeSensorDataUseCase().collect { frame ->
                _uiState.update { it.copy(latestSensorFrame = frame) }
                gestureEngine.onNewFrame(frame)
            }
        }
    }

    private fun observeInferenceState() {
        viewModelScope.launch {
            gestureEngine.state.collect { state ->
                _uiState.update { it.copy(
                    inferenceState = state,
                    showFeedbackButtons = state == InferenceState.RESULT_FROZEN
                ) }
            }
        }
    }

    private fun observeTopPredictions() {
        viewModelScope.launch {
            gestureEngine.topPredictions.collect { predictions ->
                val adjustedPredictions = predictions.map { 
                    it.copy(adjustedConfidence = feedbackRepository.applyPenalty(it.gestureName, it.confidence))
                }
                _uiState.update { it.copy(topPredictions = adjustedPredictions) }
            }
        }
    }

    private fun observeWordBuffer() {
        viewModelScope.launch {
            _wordBuffer.collect { buffer ->
                _uiState.update { it.copy(wordBuffer = buffer) }
            }
        }
    }

    private fun observeGestureResult() {
        viewModelScope.launch {
            gestureEngine.result.collect { result ->
                if (result != null) {
                    val adjusted = feedbackRepository.applyPenalty(result.gestureName, result.confidence)
                    _uiState.update { it.copy(gestureResult = result.copy(adjustedConfidence = adjusted)) }
                } else {
                    _uiState.update { it.copy(gestureResult = null) }
                }
            }
        }
    }

    fun getStats(): Map<String, Pair<Int, Int>> = feedbackRepository.getStats()

    override fun onCleared() {
        super.onCleared()
        // TTS and translator lifecycle is managed by AndroidSpeechManager / MLKitTranslationManager
    }

    fun clearError() {
        _uiState.update { it.copy(errorMessage = null) }
    }
}
