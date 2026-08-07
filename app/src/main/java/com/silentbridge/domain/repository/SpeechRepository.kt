package com.silentbridge.domain.repository

import com.silentbridge.domain.language.SupportedLanguage
import com.silentbridge.domain.speech.TtsStatus
import kotlinx.coroutines.flow.StateFlow

interface SpeechRepository {
    /** Current TTS status for the active language. */
    val ttsStatus: StateFlow<TtsStatus>

    /**
     * Speak [text] using the TTS voice for [language].
     * Switches locale and selects best available voice automatically.
     */
    suspend fun speak(text: String, language: SupportedLanguage)

    /** Stop any ongoing speech immediately. */
    fun stop()

    /** Release all TTS resources. Call from ViewModel.onCleared(). */
    fun shutdown()
}
