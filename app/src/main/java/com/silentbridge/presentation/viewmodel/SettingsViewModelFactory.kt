package com.silentbridge.presentation.viewmodel

import android.app.Application
import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.silentbridge.data.language.LanguageManager
import com.silentbridge.data.speech.AndroidSpeechManager
import com.silentbridge.data.translation.MLKitTranslationManager
import com.silentbridge.domain.usecase.ChangeLanguageUseCase
import com.silentbridge.domain.usecase.GetSupportedLanguagesUseCase
import com.silentbridge.domain.usecase.SpeakSentenceUseCase
import com.silentbridge.domain.usecase.TranslateSentenceUseCase

class SettingsViewModelFactory(private val context: Context) : ViewModelProvider.Factory {

    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        check(modelClass.isAssignableFrom(SettingsViewModel::class.java)) {
            "Unknown ViewModel class: ${modelClass.name}"
        }

        val languagePrefs = context.getSharedPreferences("silentbridge_language", Context.MODE_PRIVATE)
        val languageManager   = LanguageManager(languagePrefs)
        val translationManager = MLKitTranslationManager()
        val speechManager      = AndroidSpeechManager(context.applicationContext as Application)

        @Suppress("UNCHECKED_CAST")
        return SettingsViewModel(
            languageRepository      = languageManager,
            translationRepository   = translationManager,
            speechRepository        = speechManager,
            changeLanguageUseCase   = ChangeLanguageUseCase(languageManager),
            getSupportedLanguagesUseCase = GetSupportedLanguagesUseCase(),
            speakSentenceUseCase    = SpeakSentenceUseCase(speechManager),
            translateSentenceUseCase = TranslateSentenceUseCase(translationManager)
        ) as T
    }
}
