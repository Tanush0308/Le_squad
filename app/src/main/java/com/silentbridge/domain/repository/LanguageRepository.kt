package com.silentbridge.domain.repository

import com.silentbridge.domain.language.SupportedLanguage
import kotlinx.coroutines.flow.StateFlow

interface LanguageRepository {
    /** Currently selected output language. */
    val selectedLanguage: StateFlow<SupportedLanguage>

    /** Persist and broadcast new language selection. */
    fun setLanguage(language: SupportedLanguage)
}
