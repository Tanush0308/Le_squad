package com.silentbridge.domain.inference

object NaturalSentenceBuilder {

    fun build(structure: ParsedStructure): String {
        val subject = structure.subject
        val objects = structure.objects.map { it.lowercase() }
        val intent = structure.intent

        val subj = when {
            subject == null || subject.lowercase() == "none" -> "I"
            subject.lowercase() == "i" -> "I"
            else -> subject.lowercase().replaceFirstChar { it.uppercase() }
        }

        val objectPhrase = when (objects.size) {
            0 -> ""
            1 -> objects[0]
            2 -> "${objects[0]} and ${objects[1]}"
            else -> objects.dropLast(1).joinToString(", ") + " and ${objects.last()}"
        }

        return when (intent) {
            GestureIntent.EMERGENCY -> when {
                objectPhrase.isBlank() -> "Please help me!"
                objectPhrase.contains("help") -> "Please help me!"
                else -> "I urgently need ${objectPhrase}!"
            }
            GestureIntent.QUESTION -> when {
                objectPhrase.isBlank() -> "What do you need?"
                else -> "Do you need ${objectPhrase}?"
            }
            GestureIntent.CONFIRMATION -> "Yes."
            GestureIntent.NEGATION -> "No."
            GestureIntent.GREETING -> "Hello."
            GestureIntent.CLOSING -> "Thank you."
            else -> when { // REQUEST / UNKNOWN
                objectPhrase.isBlank() -> "$subj need help."
                else -> "$subj need ${objectPhrase}."
            }
        }
    }
}
