package com.silentbridge.data.translation

import android.util.Log
import com.google.mlkit.common.model.DownloadConditions
import com.google.mlkit.nl.translate.TranslateLanguage
import com.google.mlkit.nl.translate.Translation
import com.google.mlkit.nl.translate.Translator
import com.google.mlkit.nl.translate.TranslatorOptions
import com.silentbridge.domain.language.SupportedLanguage
import com.silentbridge.domain.repository.TranslationRepository
import com.silentbridge.domain.translation.ModelStatus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlin.coroutines.resume

private const val TAG = "MLKitTranslation"

/**
 * Offline-only translation using ML Kit.
 *
 * Design:
 * - One [Translator] instance per (EN → target) pair, cached in [translatorCache].
 * - Models are downloaded on first use (no WiFi restriction — any connection).
 * - Once downloaded, models are cached permanently by ML Kit on-device.
 * - All work runs on [Dispatchers.IO] — UI thread is never blocked.
 */
class MLKitTranslationManager : TranslationRepository {

    /** Cached translators: key = target language code */
    private val translatorCache = mutableMapOf<String, Translator>()

    private val _modelStatuses = MutableStateFlow<Map<String, ModelStatus>>(
        SupportedLanguage.all
            .filter { it != SupportedLanguage.English }
            .associate { it.code to ModelStatus.NotInstalled }
    )
    override val modelStatuses: StateFlow<Map<String, ModelStatus>> = _modelStatuses.asStateFlow()

    // No WiFi restriction — download on any connection as requested
    private val downloadConditions = DownloadConditions.Builder().build()

    override suspend fun translate(text: String, targetLanguage: SupportedLanguage): String {
        if (targetLanguage == SupportedLanguage.English) return text
        if (text.isBlank()) return text

        return withContext(Dispatchers.IO) {
            try {
                val translator = getOrCreateTranslator(targetLanguage)
                ensureModelDownloaded(translator, targetLanguage)
                translateWithTranslator(translator, text)
            } catch (e: Exception) {
                Log.e(TAG, "Translation failed for ${targetLanguage.code}: ${e.message}", e)
                text // Fallback: return English
            }
        }
    }

    override suspend fun downloadModel(language: SupportedLanguage) {
        if (language == SupportedLanguage.English) return
        withContext(Dispatchers.IO) {
            try {
                val translator = getOrCreateTranslator(language)
                ensureModelDownloaded(translator, language)
            } catch (e: Exception) {
                Log.e(TAG, "Model download failed for ${language.code}", e)
                updateStatus(language.code, ModelStatus.Failed(e.message ?: "Unknown"))
            }
        }
    }

    private fun getOrCreateTranslator(language: SupportedLanguage): Translator {
        return translatorCache.getOrPut(language.code) {
            val options = TranslatorOptions.Builder()
                .setSourceLanguage(TranslateLanguage.ENGLISH)
                .setTargetLanguage(language.mlKitCode)
                .build()
            Translation.getClient(options).also {
                Log.d(TAG, "Created new Translator for ${language.code}")
            }
        }
    }

    /** Downloads model if not already present; updates status flow. */
    private suspend fun ensureModelDownloaded(translator: Translator, language: SupportedLanguage) {
        val currentStatus = _modelStatuses.value[language.code]
        if (currentStatus is ModelStatus.Downloaded) return // Already cached

        updateStatus(language.code, ModelStatus.Downloading)
        suspendCancellableCoroutine { continuation ->
            translator.downloadModelIfNeeded(downloadConditions)
                .addOnSuccessListener {
                    updateStatus(language.code, ModelStatus.Downloaded)
                    Log.i(TAG, "Model downloaded for ${language.code}")
                    continuation.resume(Unit)
                }
                .addOnFailureListener { e ->
                    val msg = e.message ?: "Unknown"
                    updateStatus(language.code, ModelStatus.Failed(msg))
                    Log.e(TAG, "Model download failed for ${language.code}: $msg")
                    continuation.resume(Unit) // Don't throw — let translate() handle it
                }
        }
    }

    private suspend fun translateWithTranslator(translator: Translator, text: String): String {
        return suspendCancellableCoroutine { continuation ->
            translator.translate(text)
                .addOnSuccessListener { result ->
                    continuation.resume(result)
                }
                .addOnFailureListener { e ->
                    Log.e(TAG, "translate() call failed: ${e.message}")
                    continuation.resume(text) // Fallback to English
                }
        }
    }

    private fun updateStatus(code: String, status: ModelStatus) {
        _modelStatuses.update { it.toMutableMap().apply { put(code, status) } }
    }

    /** Call when the manager is no longer needed (e.g., app exit). */
    fun closeAll() {
        translatorCache.values.forEach { it.close() }
        translatorCache.clear()
    }
}
