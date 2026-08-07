package com.silentbridge.data.repository

import android.util.Log
import com.silentbridge.domain.repository.QwenInferenceRepository

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

        // Parse both "Key: Value" (inline) and legacy "Key\nValue" (next-line) formats
        fun extractInline(key: String): String? {
            return lines.firstOrNull { it.startsWith("$key:") }
                ?.removePrefix("$key:")?.trim()
                ?.takeIf { it.isNotBlank() && it.lowercase() != "none" }
                ?: extractSection(lines, key)
        }

        val intent = extractInline("Intent")
        val subject = extractInline("Subject")
        val objectsRaw = extractInline("Objects")

        val objects = objectsRaw.orEmpty()
            .split(",")
            .map { it.trim().lowercase() }
            .filter { it.isNotBlank() && it != "none" }

        val sentence = buildNaturalSentence(intent?.lowercase(), subject?.lowercase(), objects)
        return Result.success(sentence)
    }

    private fun extractSection(lines: List<String>, key: String): String? {
        return lines.dropWhile { it != key && it != "$key:" }
            .drop(1)
            .firstOrNull()
            ?.trim()
            ?.takeIf { it.isNotBlank() && it.lowercase() != "none" }
    }

    private fun buildNaturalSentence(
        intent: String?,
        subject: String?,
        objects: List<String>
    ): String {
        val subj = when {
            subject == null || subject == "none" -> "I"
            subject == "i" -> "I"
            else -> subject.replaceFirstChar { it.uppercase() }
        }

        val objectPhrase = when (objects.size) {
            0 -> ""
            1 -> objects[0]
            2 -> "${objects[0]} and ${objects[1]}"
            else -> objects.dropLast(1).joinToString(", ") + " and ${objects.last()}"
        }

        return when (intent) {
            "emergency" -> when {
                objectPhrase.isBlank() -> "Please help me!"
                objectPhrase.contains("help") -> "Please help me!"
                else -> "I urgently need $objectPhrase!"
            }
            "question" -> when {
                objectPhrase.isBlank() -> "What do you need?"
                else -> "Do you need $objectPhrase?"
            }
            "confirmation" -> "Yes."
            "negation" -> "No."
            "greeting" -> "Hello."
            "closing" -> "Thank you."
            else -> when { // request / unknown
                objectPhrase.isBlank() -> "$subj need help."
                else -> "$subj need $objectPhrase."
            }
        }
    }

    override suspend fun isModelLoaded(): Boolean = false

    override suspend fun close() { /* no-op */ }
}
