package com.silentbridge.gesture

import android.util.Log
import com.silentbridge.domain.model.SensorFrame
import com.silentbridge.domain.model.GestureResult
import com.silentbridge.ml.GestureClassifier
import com.silentbridge.ml.GesturePreprocessor
import com.silentbridge.ml.LabelMapper
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class GestureEngine(
    private val motionDetector: MotionDetector,
    private val recorder: GestureRecorder,
    private val preprocessor: GesturePreprocessor,
    private val classifier: GestureClassifier,
    private val labelMapper: LabelMapper
) {
    private val _state = MutableStateFlow(InferenceState.DISCONNECTED)
    val state: StateFlow<InferenceState> = _state.asStateFlow()

    private val _result = MutableStateFlow<GestureResult?>(null)
    val result: StateFlow<GestureResult?> = _result.asStateFlow()

    private val _topPredictions = MutableStateFlow<List<GestureResult>>(emptyList())
    val topPredictions: StateFlow<List<GestureResult>> = _topPredictions.asStateFlow()

    // Auto-loop state
    private var autoLoopActive = false

    /**
     * Callback invoked in AUTO mode when a gesture is classified.
     * The ViewModel decides whether to accept or reject the result.
     * Returning true = accepted (word added to buffer); false = discarded.
     * After this callback, the engine automatically restarts recording.
     */
    var onAutoGestureResult: ((GestureResult) -> Boolean)? = null

    private var gyroThreshold = 0.0
    private var calibrationData = mutableListOf<Double>()
    private val CALIBRATION_FRAMES = 150 // 3 seconds at 50Hz
    private var inferenceLocked = false

    private var biasGx = 0.0
    private var biasGy = 0.0
    private var biasGz = 0.0
    private var calibrationSumGx = 0.0
    private var calibrationSumGy = 0.0
    private var calibrationSumGz = 0.0
    private val calibrationFramesList = mutableListOf<SensorFrame>()

    companion object {
        private const val TAG = "GestureEngine"
        private const val GYRO_MARGIN = 15.0
    }

    fun onBluetoothConnected() {
        if (_state.value == InferenceState.DISCONNECTED) {
            _state.value = InferenceState.CONNECTED
            startCalibration()
        }
    }

    fun onBluetoothDisconnected() {
        _state.value = InferenceState.DISCONNECTED
        autoLoopActive = false
        inferenceLocked = false
    }

    private fun startCalibration() {
        _state.value = InferenceState.CALIBRATING
        calibrationData.clear()
        calibrationSumGx = 0.0
        calibrationSumGy = 0.0
        calibrationSumGz = 0.0
        calibrationFramesList.clear()
        Log.d(TAG, "Calibration started")
    }

    /** Start a single manual capture session (one gesture per tap). */
    fun startSession() {
        if (_state.value == InferenceState.READY || _state.value == InferenceState.RESULT_FROZEN || _state.value == InferenceState.ERROR) {
            autoLoopActive = false
            beginRecording()
        }
    }

    /** Start the continuous auto-capture loop (hands-free, loops until stopped). */
    fun startAutoLoop() {
        if (_state.value == InferenceState.READY || _state.value == InferenceState.RESULT_FROZEN || _state.value == InferenceState.ERROR) {
            autoLoopActive = true
            beginRecording()
            Log.d(TAG, "Auto loop started")
        }
    }

    /** Stop the auto-capture loop and return to READY. */
    fun stopAutoLoop() {
        autoLoopActive = false
        _state.value = InferenceState.READY
        _result.value = null
        _topPredictions.value = emptyList()
        recorder.clear()
        inferenceLocked = false
        Log.d(TAG, "Auto loop stopped")
    }

    private fun beginRecording() {
        _state.value = InferenceState.START_BUTTON_PRESSED
        _result.value = null
        _topPredictions.value = emptyList()
        recorder.clear()
        inferenceLocked = false
        _state.value = InferenceState.RECORDING
        recorder.start()
        Log.d(TAG, "Recording started (autoLoop=$autoLoopActive)")
    }

    fun resetEngine() {
        _state.value = InferenceState.READY
        _result.value = null
        _topPredictions.value = emptyList()
        recorder.clear()
        inferenceLocked = false
        Log.d(TAG, "Engine reset to READY")
    }

    fun stopSession() {
        autoLoopActive = false
        _state.value = InferenceState.READY
        _result.value = null
        _topPredictions.value = emptyList()
        recorder.clear()
        inferenceLocked = false
        Log.d(TAG, "Session stopped")
    }

    fun onNewFrame(frame: SensorFrame) {
        when (_state.value) {
            InferenceState.CALIBRATING -> {
                calibrationSumGx += frame.gx
                calibrationSumGy += frame.gy
                calibrationSumGz += frame.gz
                calibrationFramesList.add(frame)

                if (calibrationFramesList.size >= CALIBRATION_FRAMES) {
                    biasGx = calibrationSumGx / CALIBRATION_FRAMES
                    biasGy = calibrationSumGy / CALIBRATION_FRAMES
                    biasGz = calibrationSumGz / CALIBRATION_FRAMES

                    var maxNoise = 0.0
                    for (f in calibrationFramesList) {
                        val cx = f.gx - biasGx
                        val cy = f.gy - biasGy
                        val cz = f.gz - biasGz
                        val mag = kotlin.math.sqrt(cx * cx + cy * cy + cz * cz)
                        if (mag > maxNoise) maxNoise = mag
                    }

                    gyroThreshold = maxNoise + GYRO_MARGIN
                    Log.d(TAG, "Calibration done. Bias: Gx=$biasGx, Gy=$biasGy, Gz=$biasGz. Threshold: $gyroThreshold")
                    calibrationFramesList.clear()
                    _state.value = InferenceState.READY
                }
            }
            InferenceState.RECORDING -> {
                val calibratedFrame = frame.copy(
                    gx = (frame.gx - biasGx).toFloat(),
                    gy = (frame.gy - biasGy).toFloat(),
                    gz = (frame.gz - biasGz).toFloat()
                )
                recorder.addFrame(calibratedFrame)

                val magnitude = motionDetector.calculateMagnitude(calibratedFrame)
                val isQuiet = !motionDetector.isMoving(magnitude, gyroThreshold)
                if (recorder.updateIdleState(isQuiet)) {
                    Log.d(TAG, "Gesture ended.")
                    val gestureFrames = recorder.getValidGesture()
                    if (gestureFrames != null) {
                        Log.d(TAG, "Frames collected: ${gestureFrames.size}. Moving to GESTURE_DETECTED")
                        _state.value = InferenceState.GESTURE_DETECTED
                        processGesture(gestureFrames.toList())
                    } else {
                        Log.d(TAG, "Gesture too short. Discarding.")
                        recorder.clear()
                        recorder.start()
                    }
                }
            }
            else -> {}
        }
    }

    private fun processGesture(frames: List<SensorFrame>) {
        if (inferenceLocked) {
            Log.d(TAG, "Inference locked, skipping")
            return
        }

        _state.value = InferenceState.PREPROCESSING
        try {
            val inputTensor = preprocessor.preprocess(frames)
            _state.value = InferenceState.MODEL_INFERENCE
            val outputProbabilities = classifier.classify(inputTensor)
            _state.value = InferenceState.DISPLAY_RESULT

            Log.i(TAG, "Raw model output logits: ${outputProbabilities.contentToString()}")
            val topResults = outputProbabilities.mapIndexed { index, prob ->
                labelMapper.getLabel(index) to prob
            }.sortedByDescending { it.second }

            if (topResults.isNotEmpty()) {
                val winner = topResults[0]
                Log.i(TAG, "Final prediction: ${winner.first} (${String.format("%.4f", winner.second)})")
                topResults.take(3).forEachIndexed { i, res ->
                    Log.i(TAG, "  ${i + 1}. ${res.first} = ${String.format("%.4f", res.second)}")
                }

                val gestureResult = GestureResult(
                    gestureName = winner.first,
                    confidence = winner.second,
                    topPredictions = topResults,
                    frames = frames
                )

                if (autoLoopActive) {
                    // AUTO MODE: let ViewModel decide accept/reject, then auto-restart
                    val accepted = onAutoGestureResult?.invoke(gestureResult) ?: false
                    Log.d(TAG, "Auto mode: ${winner.first} confidence=${String.format("%.2f", winner.second)} accepted=$accepted")
                    beginRecording()
                } else {
                    // MANUAL MODE: freeze result and wait for user YES/NO
                    _result.value = gestureResult
                    _topPredictions.value = topResults.take(3).map { GestureResult(it.first, it.second) }
                    inferenceLocked = true
                    _state.value = InferenceState.RESULT_FROZEN
                    Log.d(TAG, "Inference locked. State: RESULT_FROZEN")
                }
            } else {
                if (autoLoopActive) beginRecording() else _state.value = InferenceState.ERROR
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error processing gesture: ${e.message}")
            if (autoLoopActive) beginRecording() else _state.value = InferenceState.ERROR
        }
    }
}
