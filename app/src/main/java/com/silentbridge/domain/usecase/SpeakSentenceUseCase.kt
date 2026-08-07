package com.silentbridge.domain.usecase

import com.silentbridge.domain.language.SupportedLanguage
import com.silentbridge.domain.repository.SpeechRepository

class SpeakSentenceUseCase(private val repository: SpeechRepository) {
    suspend operator fun invoke(text: String, language: SupportedLanguage) =
        repository.speak(text, language)
}
