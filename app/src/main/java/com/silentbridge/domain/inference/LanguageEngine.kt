package com.silentbridge.domain.inference

import android.util.Log
import com.silentbridge.domain.repository.QwenInferenceRepository

/**
 * Core orchestrator for the sign language sentence reconstruction pipeline.
 *
 * Pre-inference rules (to avoid SLM latency for simple signals):
 * - Standalone YES / NO → return mapped English immediately
 * - Standalone HELLO / HI / GOOD_MORNING (greeting only) → return "Hello." immediately
 * - Standalone THANK_YOU / BYE / GOODBYE → return mapped English immediately
 * - Mixed streams → strip greeting/closing, run Qwen on the core request tokens,
 *   then combine prefix/suffix with the Qwen output
 */
class LanguageEngine(
    private val interpreter: IntentInterpreter,
    private val promptBuilder: PromptBuilder,
    private val validator: SemanticValidator,
    private val repository: QwenInferenceRepository
) {
    companion object {
        private const val TAG = "LanguageEngine"

        private val GREETING_MAP = mapOf(
            "HELLO" to "Hello.",
            "HI" to "Hi.",
            "GOOD_MORNING" to "Good morning."
        )
        private val CLOSING_MAP = mapOf(
            "THANK_YOU" to "Thank you.",
            "THANKS" to "Thanks.",
            "BYE" to "Goodbye.",
            "GOODBYE" to "Goodbye."
        )
        private val CONFIRMATION_MAP = mapOf(
            "YES" to "Yes.",
            "OK" to "Okay.",
            "CORRECT" to "Correct."
        )
        private val NEGATION_MAP = mapOf(
            "NO" to "No.",
            "NOT" to "Not.",
            "INCORRECT" to "Incorrect."
        )
    }

    suspend fun reconstructSentence(tokens: List<String>): Result<String> {
        if (tokens.isEmpty()) return Result.failure(IllegalArgumentException("Empty token list"))

        val upper = tokens.map { it.uppercase().trim() }

        // --- Pre-inference fast path ---
        // Single-token deterministic responses
        if (upper.size == 1) {
            val token = upper.first()
            val mapped = CONFIRMATION_MAP[token]
                ?: NEGATION_MAP[token]
                ?: GREETING_MAP[token]
                ?: CLOSING_MAP[token]
            if (mapped != null) {
                Log.d(TAG, "Fast-path for single token '$token' → '$mapped'")
                return Result.success(mapped)
            }
        }

        // Separate greeting/closing from core tokens
        val greetingPrefix: String? = upper.firstOrNull { GREETING_MAP.containsKey(it) }
            ?.let { GREETING_MAP[it] }
        val closingSuffix: String? = upper.firstOrNull { CLOSING_MAP.containsKey(it) }
            ?.let { CLOSING_MAP[it] }

        val greetingTokens = upper.filter { GREETING_MAP.containsKey(it) }.toSet()
        val closingTokens = upper.filter { CLOSING_MAP.containsKey(it) }.toSet()
        val coreTokens = upper.filter { it !in greetingTokens && it !in closingTokens }

        // If no core request tokens remain, combine greeting/closing directly
        if (coreTokens.isEmpty()) {
            val sentence = listOfNotNull(greetingPrefix, closingSuffix).joinToString(" ")
            return Result.success(sentence.ifBlank { upper.joinToString(" ") })
        }

        // Parse the core structure and run inference
        val structure = interpreter.interpret(coreTokens)
        val prompt = promptBuilder.buildPrompt(structure)

        Log.d(TAG, "Calling QwenInference with ${coreTokens.size} core tokens")
        val inferenceResult = try {
            repository.generateSentence(prompt)
        } catch (t: Throwable) {
            // Catches native crashes (SIGSEGV propagated as Error), OOM, etc.
            Log.e(TAG, "Fatal inference error, using fallback", t)
            Result.failure(RuntimeException("Inference fatal error: ${t.message}", t))
        }

        if (inferenceResult.isFailure) {
            // Graceful fallback: rule-based join using NaturalSentenceBuilder
            val fallback = buildFallback(greetingPrefix, structure, closingSuffix)
            Log.w(TAG, "Inference failed, using fallback: $fallback")
            return Result.success(fallback)
        }

        val raw = inferenceResult.getOrNull()?.trim() ?: ""
        val cleaned = cleanOutput(raw)

        // Validate output — if it adds unauthorized words or misses key objects, fall back
        val isValid = validator.isValid(cleaned, structure, coreTokens + listOf("hello", "thank", "you"))
        val sentence = if (isValid) cleaned else NaturalSentenceBuilder.build(structure)

        val final = buildWithDecorations(greetingPrefix, sentence, closingSuffix)
        Log.d(TAG, "Final sentence: $final")
        return Result.success(final)
    }

    /** Also used for direct prompt calls (e.g. word alternatives) */
    suspend fun generateDirect(prompt: String): Result<String> {
        return repository.generateSentence(prompt)
    }

    private fun buildFallback(
        greeting: String?,
        structure: ParsedStructure,
        closing: String?
    ): String {
        val core = NaturalSentenceBuilder.build(structure)
        return buildWithDecorations(greeting, core, closing)
    }

    private fun buildWithDecorations(prefix: String?, body: String, suffix: String?): String {
        return listOfNotNull(prefix, body, suffix).joinToString(" ")
    }

    private fun cleanOutput(raw: String): String {
        // Strip any assistant continuation tokens and trim
        var cleaned = raw
            .removePrefix("<|im_start|>assistant")
            .removePrefix("<|im_end|>")
            .trim()
        // Keep only the first sentence if the model returned multiple
        val firstSentenceEnd = cleaned.indexOfFirst { it == '.' || it == '!' || it == '?' }
        if (firstSentenceEnd != -1) {
            cleaned = cleaned.substring(0, firstSentenceEnd + 1)
        }
        return cleaned
    }
}
