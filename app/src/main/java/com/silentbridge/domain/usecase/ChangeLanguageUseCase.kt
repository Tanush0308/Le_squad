package com.silentbridge.domain.usecase

import com.silentbridge.domain.language.SupportedLanguage
import com.silentbridge.domain.repository.LanguageRepository

class ChangeLanguageUseCase(private val repository: LanguageRepository) {
    operator fun invoke(language: SupportedLanguage) = repository.setLanguage(language)
}
