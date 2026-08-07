package com.silentbridge.domain.usecase

import com.silentbridge.domain.language.SupportedLanguage

class GetSupportedLanguagesUseCase {
    operator fun invoke(): List<SupportedLanguage> = SupportedLanguage.all
}
