package com.silentbridge.domain.inference

data class ParsedStructure(
    val intent: GestureIntent,
    val greeting: String? = null,
    val closing: String? = null,
    val subject: String? = null,
    val objects: List<String> = emptyList(),
    val verbs: List<String> = emptyList(),
    val isEmergency: Boolean = false
)

class IntentInterpreter {
    fun interpret(tokens: List<String>): ParsedStructure {
        if (tokens.isEmpty()) {
            return ParsedStructure(GestureIntent.UNKNOWN)
        }

        val greetingKeywords = setOf("HELLO", "HI", "GOOD_MORNING")
        val emergencyKeywords = setOf("HELP", "MEDICINE", "DANGER", "HURT", "ACCIDENT", "DOCTOR")
        val confirmationKeywords = setOf("YES", "OK", "CORRECT")
        val negationKeywords = setOf("NO", "NOT", "INCORRECT")
        val closingKeywords = setOf("THANK_YOU", "BYE", "GOODBYE", "THANKS")
        val subjectsList = setOf("I", "YOU", "WE", "HE", "SHE", "THEY")
        val verbsList = setOf("NEED", "WANT", "PLEASE", "GIVE", "GO", "COME", "GET", "LIKE")

        val tokensUpper = tokens.map { it.uppercase().trim() }

        var greeting: String? = null
        var closing: String? = null
        var subject: String? = null
        val objects = mutableListOf<String>()
        val verbs = mutableListOf<String>()
        var isEmergency = false

        for (token in tokensUpper) {
            when {
                greetingKeywords.contains(token) -> greeting = token
                closingKeywords.contains(token) -> closing = token
                subjectsList.contains(token) -> subject = token
                verbsList.contains(token) -> verbs.add(token)
                emergencyKeywords.contains(token) -> {
                    isEmergency = true
                    if (token == "HELP" || token == "MEDICINE") {
                        objects.add(token)
                    }
                }
                else -> {
                    if (token != "ALL") {
                        objects.add(token)
                    }
                }
            }
        }

        val intent = when {
            isEmergency -> GestureIntent.EMERGENCY
            greeting != null && tokensUpper.size == 1 -> GestureIntent.GREETING
            closing != null && tokensUpper.size == 1 -> GestureIntent.CLOSING
            tokensUpper.any { confirmationKeywords.contains(it) } -> GestureIntent.CONFIRMATION
            tokensUpper.any { negationKeywords.contains(it) } -> GestureIntent.NEGATION
            tokensUpper.any { it == "WANT" || it == "NEED" || it == "PLEASE" || it == "I" } -> GestureIntent.REQUEST
            tokensUpper.any { it == "YOU" || it == "WHERE" || it == "WHAT" || it == "HOW" } -> GestureIntent.QUESTION
            else -> GestureIntent.REQUEST
        }

        return ParsedStructure(
            intent = intent,
            greeting = greeting,
            closing = closing,
            subject = subject,
            objects = objects,
            verbs = verbs,
            isEmergency = isEmergency
        )
    }
}
