package com.silentbridge.domain.model

/**
 * Represents a single sign language tutorial entry.
 *
 * @param id        Unique identifier.
 * @param word      The English word being taught.
 * @param emoji     Visual emoji representing the word for quick recognition.
 * @param youtubeId YouTube video ID (used to build the embed URL). Sourced from
 *                  ASLUniversity (Dr. Bill Vicars / Lifeprint.com) — freely accessible.
 * @param description Step-by-step text description of the hand movement.
 * @param handshape  Short label for the primary handshape used (e.g., "Open B", "Index finger").
 */
data class TutorialItem(
    val id: Int,
    val word: String,
    val emoji: String,
    val youtubeId: String,
    val description: String,
    val handshape: String
)
