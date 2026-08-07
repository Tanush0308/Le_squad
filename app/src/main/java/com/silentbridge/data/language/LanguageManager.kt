package com.silentbridge.data.language

import android.content.SharedPreferences
import com.silentbridge.domain.language.SupportedLanguage
import com.silentbridge.domain.repository.LanguageRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

private const val KEY_LANG = "selected_language_code"

/**
 * Persists the selected language in SharedPreferences and broadcasts changes
 * via [selectedLanguage] StateFlow.
 */
class LanguageManager(private val prefs: SharedPreferences) : LanguageRepository {

    private val _selectedLanguage = MutableStateFlow(
        SupportedLanguage.fromCode(prefs.getString(KEY_LANG, "en") ?: "en")
    )
    override val selectedLanguage: StateFlow<SupportedLanguage> = _selectedLanguage.asStateFlow()

    override fun setLanguage(language: SupportedLanguage) {
        prefs.edit().putString(KEY_LANG, language.code).apply()
        _selectedLanguage.value = language
    }
}
