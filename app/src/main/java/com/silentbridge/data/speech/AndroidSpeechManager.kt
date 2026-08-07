package com.silentbridge.data.speech

import android.content.Context
import android.content.Intent
import android.speech.tts.TextToSpeech
import android.speech.tts.Voice
import android.util.Log
import com.silentbridge.domain.language.SupportedLanguage
import com.silentbridge.domain.repository.SpeechRepository
import com.silentbridge.domain.speech.TtsStatus
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import java.util.Locale
import kotlin.coroutines.resume

private const val TAG = "AndroidSpeechManager"

/**
 * Single-instance Android TextToSpeech manager.
 *
 * Design principles:
 * - TTS is initialized exactly once and never recreated.
 * - Language switching is done via [tts.setLanguage()] — not by recreating TTS.
 * - Best voice is selected automatically: highest-quality, non-network, locale-matching,
 *   female preferred.
 * - Status is exposed via [ttsStatus] StateFlow so the UI can react.
 * - Never blocks the calling thread — TTS callbacks run on the TTS thread.
 */
class AndroidSpeechManager(private val context: Context) : SpeechRepository {

    private var tts: TextToSpeech? = null
    private var isReady = false

    private val _ttsStatus = MutableStateFlow<TtsStatus>(TtsStatus.Initializing)
    override val ttsStatus: StateFlow<TtsStatus> = _ttsStatus.asStateFlow()

    init {
        tts = TextToSpeech(context) { status ->
            if (status == TextToSpeech.SUCCESS) {
                isReady = true
                _ttsStatus.value = TtsStatus.Available("Default")
                Log.i(TAG, "TextToSpeech initialized successfully")
            } else {
                isReady = false
                _ttsStatus.value = TtsStatus.Error
                Log.e(TAG, "TextToSpeech initialization failed with status: $status")
            }
        }
    }

    override suspend fun speak(text: String, language: SupportedLanguage) {
        if (!isReady || tts == null) {
            Log.w(TAG, "TTS not ready — skipping speak()")
            return
        }
        if (text.isBlank()) return

        val locale = language.locale
        switchToLocale(locale, language)
        tts!!.speak(text, TextToSpeech.QUEUE_FLUSH, null, "sb_${System.currentTimeMillis()}")
    }

    private fun switchToLocale(locale: Locale, language: SupportedLanguage) {
        val result = tts?.setLanguage(locale) ?: return

        when (result) {
            TextToSpeech.LANG_MISSING_DATA -> {
                Log.w(TAG, "TTS missing data for locale: $locale — launching install")
                _ttsStatus.value = TtsStatus.MissingData
                val intent = Intent(TextToSpeech.Engine.ACTION_INSTALL_TTS_DATA).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                }
                try { context.startActivity(intent) } catch (e: Exception) {
                    Log.e(TAG, "Cannot start TTS install activity", e)
                }
            }
            TextToSpeech.LANG_NOT_SUPPORTED -> {
                Log.w(TAG, "TTS locale not supported: $locale")
                _ttsStatus.value = TtsStatus.NotSupported
                // Graceful fallback: use English
                tts?.setLanguage(Locale.ENGLISH)
            }
            else -> {
                // LANG_AVAILABLE / LANG_COUNTRY_AVAILABLE / LANG_COUNTRY_VAR_AVAILABLE
                val bestVoice = selectBestVoice(locale)
                if (bestVoice != null) {
                    tts?.voice = bestVoice
                    _ttsStatus.value = TtsStatus.Available(bestVoice.name)
                    Log.d(TAG, "Switched to voice: ${bestVoice.name} for $locale")
                } else {
                    _ttsStatus.value = TtsStatus.Available("Default (${language.displayName})")
                }
            }
        }
    }

    /**
     * Selects the best available voice for [locale]:
     * 1. Non-network voices only (offline requirement)
     * 2. Locale match (language + country)
     * 3. Highest quality score
     * 4. Female voice preferred over male
     */
    private fun selectBestVoice(locale: Locale): Voice? {
        val voices = try { tts?.voices } catch (e: Exception) { null } ?: return null

        val candidates = voices.filter { voice ->
            !voice.isNetworkConnectionRequired &&
                voice.locale.language == locale.language
        }

        if (candidates.isEmpty()) return null

        return candidates
            .sortedWith(
                compareByDescending<Voice> { it.quality }
                    .thenByDescending { it.locale.country == locale.country }
                    .thenByDescending { it.name.contains("female", ignoreCase = true) ||
                        it.name.contains("f-", ignoreCase = true) }
            )
            .firstOrNull()
    }

    override fun stop() {
        tts?.stop()
    }

    override fun shutdown() {
        try {
            tts?.stop()
            tts?.shutdown()
        } catch (e: Exception) {
            Log.e(TAG, "Error during TTS shutdown", e)
        } finally {
            tts = null
            isReady = false
        }
    }
}
