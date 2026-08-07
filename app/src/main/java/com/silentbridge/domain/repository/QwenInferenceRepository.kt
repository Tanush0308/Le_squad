package com.silentbridge.domain.repository

interface QwenInferenceRepository {
    suspend fun generateSentence(prompt: String): Result<String>
    suspend fun isModelLoaded(): Boolean
    suspend fun close()
}
