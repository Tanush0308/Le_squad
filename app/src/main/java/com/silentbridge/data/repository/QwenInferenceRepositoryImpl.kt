package com.silentbridge.data.repository

import android.util.Log
import com.silentbridge.data.slm.ModelManager
import com.silentbridge.domain.repository.QwenInferenceRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout

class QwenInferenceRepositoryImpl(
    private val modelManager: ModelManager,
    private val modelPath: String,
    private val maxTokens: Int = 40
) : QwenInferenceRepository {

    companion object {
        private const val TAG = "QwenInferenceRepo"
        // Hard 15s timeout — if Qwen takes longer than this, cancel and fall back.
        // Prevents the OS from killing the entire app due to hung inference.
        private const val INFERENCE_TIMEOUT_MS = 15_000L
    }

    override suspend fun generateSentence(prompt: String): Result<String> {
        return withContext(Dispatchers.Default) {
            try {
                val output = withTimeout(INFERENCE_TIMEOUT_MS) {
                    modelManager.generateSafe(prompt, modelPath, maxTokens)
                }
                Log.d(TAG, "Raw output: ${output.take(120)}")
                Result.success(output.trim())
            } catch (e: TimeoutCancellationException) {
                Log.w(TAG, "Inference timed out after ${INFERENCE_TIMEOUT_MS}ms — using fallback")
                Result.failure(RuntimeException("Inference timeout"))
            } catch (e: Exception) {
                Log.e(TAG, "Inference error: ${e.message}", e)
                Result.failure(e)
            } catch (e: Error) {
                // Native crash / OOM — catch Error explicitly to avoid app kill
                Log.e(TAG, "Inference native error: ${e.message}", e)
                Result.failure(RuntimeException("Inference native error: ${e.message}", e))
            }
        }
    }

    override suspend fun isModelLoaded(): Boolean {
        return try {
            withTimeout(5_000L) {
                modelManager.getOrInitializeInference(modelPath, maxTokens)
                true
            }
        } catch (e: Exception) {
            Log.w(TAG, "Model not loaded: ${e.message}")
            false
        } catch (e: Error) {
            false
        }
    }

    override suspend fun close() {
        modelManager.close()
    }
}
