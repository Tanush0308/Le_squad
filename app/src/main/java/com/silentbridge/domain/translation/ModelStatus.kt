package com.silentbridge.domain.translation

/** Download / readiness status for a single ML Kit translation model. */
sealed class ModelStatus {
    data object NotInstalled : ModelStatus()
    data object Downloading : ModelStatus()
    data object Downloaded : ModelStatus()
    data class Failed(val reason: String) : ModelStatus()
}
