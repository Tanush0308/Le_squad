package com.silentbridge.presentation.viewmodel

import android.app.Application
import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.silentbridge.data.bluetooth.BluetoothConnectionManager
import com.silentbridge.data.bluetooth.BluetoothController
import com.silentbridge.data.bluetooth.BluetoothReader
import com.silentbridge.data.bluetooth.SensorPacketParser
import com.silentbridge.data.feedback.FeedbackExporter
import com.silentbridge.data.feedback.FeedbackLogger
import com.silentbridge.data.repository.BluetoothRepositoryImpl
import com.silentbridge.data.repository.FeedbackRepository
import com.silentbridge.data.repository.FallbackQwenRepository
import com.silentbridge.domain.inference.IntentInterpreter
import com.silentbridge.domain.inference.LanguageEngine
import com.silentbridge.domain.inference.PromptBuilder
import com.silentbridge.domain.inference.SemanticValidator
import com.silentbridge.domain.repository.QwenInferenceRepository
import com.silentbridge.domain.usecase.*
import com.silentbridge.gesture.GestureEngine
import com.silentbridge.gesture.GestureRecorder
import com.silentbridge.gesture.MotionDetector
import com.silentbridge.ml.*


class ViewModelFactory(private val context: Context) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(MainViewModel::class.java)) {
            // Bluetooth stack
            val controller = BluetoothController(context)
            val connectionManager = BluetoothConnectionManager(context)
            val parser = SensorPacketParser()
            val reader = BluetoothReader(parser)
            val repository = BluetoothRepositoryImpl(controller, connectionManager, reader)

            // Feedback stack
            val feedbackRepository = FeedbackRepository(context)
            val feedbackLogger = FeedbackLogger(context)
            val feedbackExporter = FeedbackExporter(context, feedbackLogger)

            // Gesture engine stack
            val motionDetector = MotionDetector()
            val recorder = GestureRecorder()
            val featureExtractor = FeatureExtractor()
            val resampler = Resampler()
            val scaler = FeatureScaler(context).apply { load() }
            val preprocessor = GesturePreprocessor(featureExtractor, resampler, scaler)
            val classifier = GestureClassifier(context).apply { load() }
            val labelMapper = LabelMapper(context).apply { load() }
            val gestureEngine = GestureEngine(
                motionDetector, recorder, preprocessor, classifier, labelMapper
            )

            // STABILITY FIX: The Qwen 0.5B model running on CPU raises device temperature
            // to 58°C+, causing the kernel to issue SIGKILL (signal 9) — unkillable from JVM.
            // We use the FallbackQwenRepository which produces natural sentences
            // (e.g. "I need water and food.") using the structured intent/objects parsed
            // by LanguageEngine — no model load, no thermal risk, no crashes.
            val qwenRepository: QwenInferenceRepository = FallbackQwenRepository()

            // Domain inference stack
            val interpreter = IntentInterpreter()
            val promptBuilder = PromptBuilder()
            val semanticValidator = SemanticValidator()
            val languageEngine = LanguageEngine(interpreter, promptBuilder, semanticValidator, qwenRepository)
            val languageEngineUseCase = LanguageEngineUseCase(languageEngine)

            // ── Translation / Speech / Language stack ──────────────────────────
            val languagePrefs = context.getSharedPreferences("silentbridge_language", Context.MODE_PRIVATE)
            val languageManager = com.silentbridge.data.language.LanguageManager(languagePrefs)

            val translationManager = com.silentbridge.data.translation.MLKitTranslationManager()

            val speechManager = com.silentbridge.data.speech.AndroidSpeechManager(
                context.applicationContext as Application
            )

            val translateSentenceUseCase = TranslateSentenceUseCase(translationManager)
            val speakSentenceUseCase = SpeakSentenceUseCase(speechManager)
            val changeLanguageUseCase = ChangeLanguageUseCase(languageManager)
            // ───────────────────────────────────────────────────────────────────

            @Suppress("UNCHECKED_CAST")
            return MainViewModel(
                context.applicationContext as Application,
                GetPairedDevicesUseCase(repository),
                ConnectDeviceUseCase(repository),
                DisconnectDeviceUseCase(repository),
                ObserveSensorDataUseCase(repository),
                ObserveConnectionStateUseCase(repository),
                gestureEngine,
                feedbackRepository,
                feedbackLogger,
                feedbackExporter,
                languageEngineUseCase,
                languageManager,
                translateSentenceUseCase,
                speakSentenceUseCase,
                changeLanguageUseCase
            ) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
    }
}
