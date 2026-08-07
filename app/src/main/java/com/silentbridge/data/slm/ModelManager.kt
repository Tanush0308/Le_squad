package com.silentbridge.data.slm

import android.content.Context
import android.util.Log
import com.google.mediapipe.tasks.genai.llminference.LlmInference
import java.io.File
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class ModelManager(private val context: Context) {

    companion object {
        private const val TAG = "ModelManager"
    }

    private var llmInference: LlmInference? = null

    // Mutex guards both init AND generation — LlmInference is NOT thread-safe
    val inferenceMutex = Mutex()

    suspend fun getOrInitializeInference(modelPath: String, maxTokens: Int = 40): LlmInference {
        return inferenceMutex.withLock {
            val existing = llmInference
            if (existing != null) {
                existing
            } else {
                val modelFile = File(modelPath)
                if (!modelFile.exists()) {
                    throw IllegalArgumentException(
                        "Model file not found at: $modelPath — place a .task file in app's filesDir"
                    )
                }

                Log.i(TAG, "Initializing LlmInference from: $modelPath (maxTokens=$maxTokens)")

                val options = LlmInference.LlmInferenceOptions.builder()
                    .setModelPath(modelPath)
                    .setMaxTokens(maxTokens)
                    .setPreferredBackend(LlmInference.Backend.CPU)
                    .build()

                val inference = LlmInference.createFromOptions(context, options)
                llmInference = inference
                Log.i(TAG, "LlmInference initialized successfully")
                inference
            }
        }
    }

    suspend fun generateSafe(prompt: String, modelPath: String, maxTokens: Int = 40): String {
        return inferenceMutex.withLock {
            val inference = getOrInitializeInferenceUnlocked(modelPath, maxTokens)
            inference.generateResponse(prompt)
        }
    }

    // Internal version that does NOT acquire the lock (used when lock is already held)
    private fun getOrInitializeInferenceUnlocked(modelPath: String, maxTokens: Int): LlmInference {
        val existing = llmInference
        if (existing != null) return existing

        val modelFile = File(modelPath)
        if (!modelFile.exists()) {
            throw IllegalArgumentException("Model file not found at: $modelPath")
        }

        val options = LlmInference.LlmInferenceOptions.builder()
            .setModelPath(modelPath)
            .setMaxTokens(maxTokens)
            .setPreferredBackend(LlmInference.Backend.CPU)
            .build()

        val inference = LlmInference.createFromOptions(context, options)
        llmInference = inference
        return inference
    }

    suspend fun close() {
        inferenceMutex.withLock {
            try {
                llmInference?.close()
            } catch (e: Exception) {
                Log.e(TAG, "Error closing LlmInference", e)
            } finally {
                llmInference = null
            }
        }
    }
}
