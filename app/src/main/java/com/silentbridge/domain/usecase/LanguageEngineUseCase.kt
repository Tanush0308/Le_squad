package com.silentbridge.domain.usecase

import com.silentbridge.domain.inference.LanguageEngine

class LanguageEngineUseCase(private val engine: LanguageEngine) {
    suspend fun reconstructSentence(tokens: List<String>): Result<String> =
        engine.reconstructSentence(tokens)

    suspend fun generateDirect(prompt: String): Result<String> =
        engine.generateDirect(prompt)
}
