package com.silentbridge.presentation.viewmodel

import android.app.Application
import android.content.Context
import android.speech.tts.TextToSpeech
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.ai.client.generativeai.GenerativeModel
import com.google.ai.client.generativeai.type.generationConfig
import com.google.mlkit.common.model.DownloadConditions
import com.google.mlkit.common.model.RemoteModelManager
import com.google.mlkit.nl.translate.TranslateLanguage
import com.google.mlkit.nl.translate.Translation
import com.google.mlkit.nl.translate.Translator
import com.google.mlkit.nl.translate.TranslatorOptions
import com.silentbridge.data.feedback.FeedbackExporter
import com.silentbridge.data.feedback.FeedbackLogger
import com.silentbridge.data.repository.FeedbackRepository
import com.silentbridge.domain.model.BluetoothDeviceDomain
import com.silentbridge.domain.model.ConnectionState
import com.silentbridge.domain.model.GestureResult
import com.silentbridge.domain.model.SensorFrame
import com.silentbridge.domain.usecase.ConnectDeviceUseCase
import com.silentbridge.domain.usecase.DisconnectDeviceUseCase
import com.silentbridge.domain.usecase.GetPairedDevicesUseCase
import com.silentbridge.domain.usecase.ObserveConnectionStateUseCase
import com.silentbridge.domain.usecase.ObserveSensorDataUseCase
import com.silentbridge.gesture.GestureEngine
import com.silentbridge.gesture.InferenceState
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import org.json.JSONArray
import org.json.JSONObject
import java.util.Locale

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
    val targetLanguageCode: String = "en",
    val isDownloadingModel: Boolean = false,
    val modelDownloadProgress: Float = 0f
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
    private val feedbackExporter: FeedbackExporter
) : ViewModel() {

    private val prefs = application.getSharedPreferences("silentbridge_feedback", Context.MODE_PRIVATE)
    private var tts: TextToSpeech? = null
    private var translator: Translator? = null

    private val GEMINI_API_KEY = "YOUR_GEMINI_API_KEY_HERE"
    private val GROQ_API_KEY = "YOUR_GROQ_API_KEY_HERE"
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
        val savedLang = prefs.getString(KEY_TARGET_LANG, "en") ?: "en"
        _uiState.update { it.copy(targetLanguageCode = savedLang) }
        
        initTTS()
        initTranslator(savedLang)
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

    private fun initTTS() {
        tts = TextToSpeech(application) { status ->
            if (status != TextToSpeech.SUCCESS) {
                Log.e("SilentBridge", "TTS Initialization failed!")
            } else {
                updateTTSLanguage(_uiState.value.targetLanguageCode)
            }
        }
    }

    private fun updateTTSLanguage(langCode: String) {
        val locale = when (langCode) {
            "hi" -> Locale("hi", "IN")
            "mr" -> Locale("mr", "IN")
            "gu" -> Locale("gu", "IN")
            "ta" -> Locale("ta", "IN")
            "te" -> Locale("te", "IN")
            "kn" -> Locale("kn", "IN")
            else -> Locale.ENGLISH
        }
        tts?.language = locale
    }

    fun setTargetLanguage(langCode: String) {
        if (_uiState.value.targetLanguageCode == langCode) return
        
        prefs.edit().putString(KEY_TARGET_LANG, langCode).apply()
        _uiState.update { it.copy(targetLanguageCode = langCode, translatedSentence = null) }
        updateTTSLanguage(langCode)
        initTranslator(langCode)
    }

    private fun initTranslator(langCode: String) {
        translator?.close()
        if (langCode == "en") {
            translator = null
            return
        }

        val options = TranslatorOptions.Builder()
            .setSourceLanguage(TranslateLanguage.ENGLISH)
            .setTargetLanguage(langCode)
            .build()
        
        val newTranslator = Translation.getClient(options)
        
        _uiState.update { it.copy(isDownloadingModel = true) }
        
        val conditions = DownloadConditions.Builder()
            .requireWifi()
            .build()
            
        newTranslator.downloadModelIfNeeded(conditions)
            .addOnSuccessListener {
                _uiState.update { it.copy(isDownloadingModel = false) }
                translator = newTranslator
                Log.i("SilentBridge", "Translation model for $langCode ready")
                // If we already have a sentence, translate it now
                _uiState.value.formedSentence?.let { translateSentence(it) }
            }
            .addOnFailureListener { e ->
                _uiState.update { it.copy(isDownloadingModel = false, sentenceError = "Model download failed: ${e.message}") }
                Log.e("SilentBridge", "Translation model download failed", e)
            }
    }

    private fun translateSentence(text: String) {
        val currentTranslator = translator
        if (currentTranslator == null) {
            _uiState.update { it.copy(translatedSentence = text) }
            return
        }

        currentTranslator.translate(text)
            .addOnSuccessListener { translatedText ->
                _uiState.update { it.copy(translatedSentence = translatedText) }
            }
            .addOnFailureListener { e ->
                Log.e("SilentBridge", "Translation failed", e)
                _uiState.update { it.copy(translatedSentence = text) } // Fallback to English
            }
    }

    private suspend fun makeGroqRequest(prompt: String): String? = withContext(Dispatchers.IO) {
        try {
            val url = java.net.URL("https://api.groq.com/openai/v1/chat/completions")
            val connection = url.openConnection() as java.net.HttpURLConnection
            connection.requestMethod = "POST"
            connection.setRequestProperty("Content-Type", "application/json")
            connection.setRequestProperty("Authorization", "Bearer $GROQ_API_KEY")
            connection.doOutput = true
            connection.connectTimeout = 8000
            connection.readTimeout = 15000

            val jsonBody = JSONObject()
            jsonBody.put("model", "llama-3.3-70b-versatile")
            jsonBody.put("temperature", 0.0)
            jsonBody.put("max_tokens", 60)
            
            val messages = JSONArray()
            val systemMsg = "You are an expert sign language to English interpreter. Task: Translate the provided sequence of sign language words (glosses) into a natural, grammatically correct English sentence. Rules: 1. Convey the exact meaning of the signed words. 2. You may add necessary articles, prepositions, pronouns, or conjugations to make the sentence sound natural. 3. Do not add extra context or information that was not signed. 4. Reply ONLY with the final sentence text."
            messages.put(JSONObject().put("role", "system").put("content", systemMsg))
            messages.put(JSONObject().put("role", "user").put("content", prompt))
            jsonBody.put("messages", messages)

            connection.outputStream.use { it.write(jsonBody.toString().toByteArray(Charsets.UTF_8)) }

            val responseCode = connection.responseCode
            val stream = if (responseCode in 200..299) connection.inputStream else connection.errorStream
            val response = stream.bufferedReader().use { it.readText() }

            if (responseCode in 200..299) {
                val json = JSONObject(response)
                json.optJSONArray("choices")
                    ?.optJSONObject(0)
                    ?.optJSONObject("message")
                    ?.optString("content")
                    ?.trim()
            } else {
                Log.e("SilentBridge", "Groq error $responseCode: $response")
                null
            }
        } catch (e: Exception) {
            Log.e("SilentBridge", "Groq exception: ${e.message}")
            null
        }
    }

    private suspend fun makeRawGeminiRequest(prompt: String): String? = withContext(Dispatchers.IO) {
        try {
            val url = java.net.URL("https://generativelanguage.googleapis.com/v1beta/models/gemini-2.0-flash-lite:generateContent?key=${GEMINI_API_KEY}")
            val connection = url.openConnection() as java.net.HttpURLConnection
            connection.requestMethod = "POST"
            connection.setRequestProperty("Content-Type", "application/json")
            connection.doOutput = true
            connection.connectTimeout = 8000
            connection.readTimeout = 15000

            val jsonBody = JSONObject()
            val contents = JSONArray()
            val contentObj = JSONObject()
            val parts = JSONArray()
            parts.put(JSONObject().put("text", "SIGN LANGUAGE INTERPRETER: Translate the sequence of concepts into a natural English sentence. Add necessary articles/grammar but no extra context. Reply ONLY with the sentence text. USER: $prompt"))
            contentObj.put("parts", parts)
            contents.put(contentObj)
            jsonBody.put("contents", contents)
            
            val config = JSONObject()
            config.put("temperature", 0.0)
            config.put("maxOutputTokens", 60)
            jsonBody.put("generationConfig", config)

            connection.outputStream.use { it.write(jsonBody.toString().toByteArray(Charsets.UTF_8)) }

            val responseCode = connection.responseCode
            val stream = if (responseCode in 200..299) connection.inputStream else connection.errorStream
            val responseString = stream.bufferedReader().use { it.readText() }

            if (responseCode in 200..299) {
                val jsonObject = JSONObject(responseString)
                val candidates = jsonObject.optJSONArray("candidates")
                if (candidates != null && candidates.length() > 0) {
                    val content = candidates.getJSONObject(0).optJSONObject("content")
                    val partsRes = content?.optJSONArray("parts")
                    if (partsRes != null && partsRes.length() > 0) {
                        return@withContext partsRes.getJSONObject(0).optString("text")
                    }
                }
                null
            } else {
                Log.e("SilentBridge", "Gemini error: $responseString")
                null
            }
        } catch (e: Exception) {
            Log.e("SilentBridge", "Gemini exception: ${e.message}")
            null
        }
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
            } catch (e: Exception) {
                _uiState.update { it.copy(
                    isFormingSentence = false,
                    sentenceError = "AI Error: ${e.message ?: "Unknown error"}"
                ) }
            }
        }
    }

    private suspend fun callGeminiNano(words: List<String>): String? {
        val count = words.size
        val wordList = words.joinToString(", ")
        
        val prompt = "Sign language tokens: $wordList\nTask: Translate these tokens into a single, natural-sounding English sentence. Reply ONLY with the sentence text."

        return try {
            val groqResult = makeGroqRequest(prompt)
            if (groqResult != null) {
                Log.i("SilentBridge", "Translated via Groq: $groqResult")
                return groqResult
            }
            
            val geminiResult = makeRawGeminiRequest(prompt)
            Log.i("SilentBridge", "Groq failed, Gemini result: $geminiResult")
            geminiResult?.trim()?.takeIf { it.isNotBlank() }
        } catch (e: Exception) {
            Log.e("SilentBridge", "Translation failed: ${e.message}")
            throw e
        }
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
            val prompt = """
                Sentence: "$sentence"
                Suggest exactly 3 alternative single words for "$word".
                Reply with ONLY the 3 words separated by commas.
            """.trimIndent()

            val text = makeGroqRequest(prompt) ?: makeRawGeminiRequest(prompt)
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

    fun speakSentence(sentence: String) {
        tts?.speak(sentence, TextToSpeech.QUEUE_FLUSH, null, "sentence_${System.currentTimeMillis()}")
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
        tts?.shutdown()
        translator?.close()
    }

    fun clearError() {
        _uiState.update { it.copy(errorMessage = null) }
    }
}
