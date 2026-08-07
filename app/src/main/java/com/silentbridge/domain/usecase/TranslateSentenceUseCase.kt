package com.silentbridge.domain.usecase

import com.silentbridge.domain.language.SupportedLanguage
import com.silentbridge.domain.repository.TranslationRepository

class TranslateSentenceUseCase(private val repository: TranslationRepository) {
    /** Returns translated text, or the original English text on failure. */
    suspend operator fun invoke(text: String, targetLanguage: SupportedLanguage): String =
        repository.translate(text, targetLanguage)
}
