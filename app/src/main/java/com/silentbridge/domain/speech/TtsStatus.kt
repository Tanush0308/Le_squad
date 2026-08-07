package com.silentbridge.domain.speech

/** Status of Android TTS for a given language. */
sealed class TtsStatus {
    data object Initializing : TtsStatus()
    data class Available(val voiceName: String) : TtsStatus()
    data object MissingData : TtsStatus()
    data object NotSupported : TtsStatus()
    data object Error : TtsStatus()
}
