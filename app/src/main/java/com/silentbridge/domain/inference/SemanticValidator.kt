package com.silentbridge.domain.inference

/**
 * Leniently validates that the SLM output is at least related to the original gesture tokens.
 *
 * Strategy: instead of rejecting any word not in the allowed list (too strict),
 * we check that the generated sentence contains a minimum overlap with the original tokens.
 * This allows the SLM to freely add grammar words, verbs, and articles while still
 * catching completely hallucinated/off-topic outputs.
 */
class SemanticValidator {

    private val allowedHelperWords = setOf(
        "a", "an", "the", "is", "am", "are", "to", "of", "for",
        "on", "in", "with", "and", "or", "need", "want", "please",
        "give", "me", "some", "do", "does", "did", "have", "has",
        "can", "could", "will", "would", "should", "may", "might",
        "i", "you", "we", "he", "she", "they", "it", "my", "your",
        "urgently", "now", "help", "get", "go", "come", "here",
        "where", "what", "how", "who", "there", "this", "that"
    )

    /**
     * Returns true if the generated sentence is semantically related to the original tokens.
     * Checks that:
     * 1. If any key object is defined, it MUST be present in the generated sentence.
     * 2. Overlap is verified with tokens.
     */
    fun isValid(generated: String, structure: ParsedStructure, originalTokens: List<String>): Boolean {
        if (generated.isBlank()) return false

        val lowerGen = generated.lowercase()

        // 1. Mandatory Object verification: If original tokens had object concepts,
        // they must be represented in the generated text.
        for (obj in structure.objects) {
            val lowerObj = obj.lowercase()
            // Check if the object or a reasonable singular/plural/stem is in the sentence
            if (!lowerGen.contains(lowerObj)) {
                return false
            }
        }

        // Build token set: split underscores (THANK_YOU → thank + you)
        val tokenSet = mutableSetOf<String>()
        for (t in originalTokens) {
            val lower = t.lowercase()
            if (lower.contains('_')) {
                lower.split('_').forEach { part -> if (part.isNotBlank()) tokenSet.add(part) }
            } else {
                tokenSet.add(lower)
            }
        }

        // Count how many original tokens appear in the generated sentence
        val matchCount = tokenSet.count { token ->
            lowerGen.contains(token)
        }

        // Accept if at least 1 original token appears, OR it's a very short output
        // that consists entirely of helper/grammar words (e.g., "Yes.", "No.", "Please help.")
        if (matchCount >= 1) return true

        // Secondary: check if all words are either helper words or short known patterns
        val words = lowerGen.split(Regex("[\\s,.;!?:']+")).filter { it.isNotBlank() }
        val unknownCount = words.count { it !in allowedHelperWords && it.length > 1 }
        return unknownCount == 0
    }
}

