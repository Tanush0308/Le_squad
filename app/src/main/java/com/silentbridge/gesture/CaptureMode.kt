package com.silentbridge.gesture

/**
 * Defines how gesture capture is triggered by the user.
 *
 * [AUTO]   — The engine continuously captures gestures in a loop after a single "Start".
 *            Only high-confidence predictions (≥ AUTO_CONFIDENCE_THRESHOLD) are accepted.
 *            Low-confidence gestures are silently discarded. No YES/NO feedback shown.
 *
 * [MANUAL] — The user taps "Capture Sign" for each gesture. YES/NO feedback is shown
 *            after each recognition, allowing the user to correct errors.
 */
enum class CaptureMode {
    AUTO,
    MANUAL;

    companion object {
        /** Minimum model confidence to accept a gesture in AUTO mode. */
        const val AUTO_CONFIDENCE_THRESHOLD = 0.80f

        /** Delay in ms between a gesture completing and the next recording starting in AUTO mode. */
        const val AUTO_LOOP_RESTART_DELAY_MS = 600L
    }
}
