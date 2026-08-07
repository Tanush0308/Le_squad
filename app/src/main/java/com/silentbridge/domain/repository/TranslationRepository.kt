package com.silentbridge.domain.repository

import com.silentbridge.domain.language.SupportedLanguage
import com.silentbridge.domain.translation.ModelStatus
import kotlinx.coroutines.flow.StateFlow

interface TranslationRepository {
    /**
     * Translate [text] from English to [targetLanguage].
     * Downloads the model on first use, then operates fully offline.
     * Returns the original text if translation fails.
     */
    suspend fun translate(text: String, targetLanguage: SupportedLanguage): String

    /**
     * Download status for each language's ML Kit model.
     * Key = SupportedLanguage.code
     */
    val modelStatuses: StateFlow<Map<String, ModelStatus>>

    /** Explicitly request a model download (user-initiated from settings). */
    suspend fun downloadModel(language: SupportedLanguage)
}
