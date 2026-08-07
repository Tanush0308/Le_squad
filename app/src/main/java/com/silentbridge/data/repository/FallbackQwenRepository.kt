package com.silentbridge.data.repository

import android.util.Log
import com.silentbridge.domain.repository.QwenInferenceRepository
import com.silentbridge.domain.inference.NaturalSentenceBuilder
import com.silentbridge.domain.inference.ParsedStructure
import com.silentbridge.domain.inference.GestureIntent

/**
 * Rule-based fallback used when no model file is present or hardware is incompatible.
 * Produces natural English sentences from structured prompt data without any SLM.
 */
class FallbackQwenRepository : QwenInferenceRepository {

    companion object {
        private const val TAG = "FallbackQwenRepo"
    }

    override suspend fun generateSentence(prompt: String): Result<String> {
        Log.w(TAG, "Using fallback repository — no SLM model file available")

        val lines = prompt.lines().map { it.trim() }

        // Parse "Key: Value" formats
        fun extractInline(key: String): String? {
            return lines.firstOrNull { it.startsWith("$key:") }
                ?.removePrefix("$key:")?.trim()
                ?.takeIf { it.isNotBlank() && it.lowercase() != "none" }
        }

        val intent = extractInline("Intent")
        val subject = extractInline("Subject")
        val objectsRaw = extractInline("Objects")

        val objects = objectsRaw.orEmpty()
            .split(",")
            .map { it.trim().lowercase() }
            .filter { it.isNotBlank() && it != "none" }

        val sentence = NaturalSentenceBuilder.build(
            ParsedStructure(
                intent = when (intent?.lowercase()) {
                    "emergency" -> GestureIntent.EMERGENCY
                    "question" -> GestureIntent.QUESTION
                    "confirmation" -> GestureIntent.CONFIRMATION
                    "negation" -> GestureIntent.NEGATION
                    "greeting" -> GestureIntent.GREETING
                    "closing" -> GestureIntent.CLOSING
                    else -> GestureIntent.REQUEST
                },
                subject = subject,
                objects = objects
            )
        )
        return Result.success(sentence)
    }

    override suspend fun isModelLoaded(): Boolean = false

    override suspend fun close() { /* no-op */ }
}
