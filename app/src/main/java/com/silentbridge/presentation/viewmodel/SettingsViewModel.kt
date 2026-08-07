package com.silentbridge.presentation.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.silentbridge.domain.language.SupportedLanguage
import com.silentbridge.domain.repository.LanguageRepository
import com.silentbridge.domain.repository.SpeechRepository
import com.silentbridge.domain.repository.TranslationRepository
import com.silentbridge.domain.speech.TtsStatus
import com.silentbridge.domain.translation.ModelStatus
import com.silentbridge.domain.usecase.ChangeLanguageUseCase
import com.silentbridge.domain.usecase.GetSupportedLanguagesUseCase
import com.silentbridge.domain.usecase.SpeakSentenceUseCase
import com.silentbridge.domain.usecase.TranslateSentenceUseCase
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class VoiceSettingsUiState(
    val languages: List<SupportedLanguage> = SupportedLanguage.all,
    val selectedLanguage: SupportedLanguage = SupportedLanguage.English,
    val modelStatuses: Map<String, ModelStatus> = emptyMap(),
    val ttsStatus: TtsStatus = TtsStatus.Initializing,
    val isTesting: Boolean = false
)

class SettingsViewModel(
    private val languageRepository: LanguageRepository,
    private val translationRepository: TranslationRepository,
    private val speechRepository: SpeechRepository,
    private val changeLanguageUseCase: ChangeLanguageUseCase,
    private val getSupportedLanguagesUseCase: GetSupportedLanguagesUseCase,
    private val speakSentenceUseCase: SpeakSentenceUseCase,
    private val translateSentenceUseCase: TranslateSentenceUseCase
) : ViewModel() {

    private val _uiState = MutableStateFlow(VoiceSettingsUiState())
    val uiState: StateFlow<VoiceSettingsUiState> = _uiState.asStateFlow()

    init {
        observeLanguage()
        observeModelStatuses()
        observeTtsStatus()
    }

    private fun observeLanguage() {
        viewModelScope.launch {
            languageRepository.selectedLanguage.collect { lang ->
                _uiState.update { it.copy(selectedLanguage = lang) }
            }
        }
    }

    private fun observeModelStatuses() {
        viewModelScope.launch {
            translationRepository.modelStatuses.collect { statuses ->
                _uiState.update { it.copy(modelStatuses = statuses) }
            }
        }
    }

    private fun observeTtsStatus() {
        viewModelScope.launch {
            speechRepository.ttsStatus.collect { status ->
                _uiState.update { it.copy(ttsStatus = status) }
            }
        }
    }

    fun selectLanguage(language: SupportedLanguage) {
        changeLanguageUseCase(language)
    }

    /** Download the ML Kit model for [language] explicitly. */
    fun downloadModel(language: SupportedLanguage) {
        viewModelScope.launch {
            translationRepository.downloadModel(language)
        }
    }

    /** Speak a test sentence in the selected language. */
    fun testVoice() {
        val lang = _uiState.value.selectedLanguage
        viewModelScope.launch {
            _uiState.update { it.copy(isTesting = true) }
            val testSentence = "I need water."
            val translated = if (lang == SupportedLanguage.English) testSentence
                           else translateSentenceUseCase(testSentence, lang)
            speakSentenceUseCase(translated, lang)
            _uiState.update { it.copy(isTesting = false) }
        }
    }
}
