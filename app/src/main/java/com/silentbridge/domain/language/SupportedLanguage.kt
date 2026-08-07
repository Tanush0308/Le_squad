package com.silentbridge.domain.language

import com.google.mlkit.nl.translate.TranslateLanguage
import java.util.Locale

/**
 * Sealed class representing all languages the app can translate into and speak.
 *
 * Adding a new language: simply add a new data object below with the correct
 * ML Kit language code and locale. No other files need changing except ViewModelFactory
 * to register a model download entry.
 */
sealed class SupportedLanguage(
    val code: String,
    val displayName: String,
    val nativeName: String,
    val mlKitCode: String,
    val locale: Locale
) {
    data object English : SupportedLanguage(
        code = "en",
        displayName = "English",
        nativeName = "English",
        mlKitCode = TranslateLanguage.ENGLISH,
        locale = Locale.ENGLISH
    )

    data object Hindi : SupportedLanguage(
        code = "hi",
        displayName = "Hindi",
        nativeName = "हिंदी",
        mlKitCode = TranslateLanguage.HINDI,
        locale = Locale("hi", "IN")
    )

    data object Marathi : SupportedLanguage(
        code = "mr",
        displayName = "Marathi",
        nativeName = "मराठी",
        mlKitCode = TranslateLanguage.MARATHI,
        locale = Locale("mr", "IN")
    )

    data object Gujarati : SupportedLanguage(
        code = "gu",
        displayName = "Gujarati",
        nativeName = "ગુજરાતી",
        mlKitCode = TranslateLanguage.GUJARATI,
        locale = Locale("gu", "IN")
    )

    data object Tamil : SupportedLanguage(
        code = "ta",
        displayName = "Tamil",
        nativeName = "தமிழ்",
        mlKitCode = TranslateLanguage.TAMIL,
        locale = Locale("ta", "IN")
    )

    data object Telugu : SupportedLanguage(
        code = "te",
        displayName = "Telugu",
        nativeName = "తెలుగు",
        mlKitCode = TranslateLanguage.TELUGU,
        locale = Locale("te", "IN")
    )

    data object Kannada : SupportedLanguage(
        code = "kn",
        displayName = "Kannada",
        nativeName = "ಕನ್ನಡ",
        mlKitCode = TranslateLanguage.KANNADA,
        locale = Locale("kn", "IN")
    )

    data object Malayalam : SupportedLanguage(
        code = "ml",
        displayName = "Malayalam",
        nativeName = "മലയാളം",
        mlKitCode = "ml",  // TranslateLanguage.MALAYALAM may not exist in older SDK versions
        locale = Locale("ml", "IN")
    )

    companion object {
        /** All languages in display order. */
        val all: List<SupportedLanguage> = listOf(
            English, Hindi, Marathi, Gujarati, Tamil, Telugu, Kannada, Malayalam
        )

        /** Find by saved code string (safe, defaults to English). */
        fun fromCode(code: String): SupportedLanguage =
            all.firstOrNull { it.code == code } ?: English
    }
}
