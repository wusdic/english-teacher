package com.englishteacher.core.domain.model

/**
 * One line in a conversation transcript.
 *
 * For a [Speaker.TUTOR] message, [corrections] holds feedback on the *preceding* learner
 * utterance and [repeatTarget] is the sentence the learner is invited to repeat aloud.
 */
data class ChatMessage(
    val id: String,
    val speaker: Speaker,
    val text: String,
    val timestampMillis: Long,
    val corrections: List<Correction> = emptyList(),
    val repeatTarget: String? = null,
) {
    val hasCorrections: Boolean get() = corrections.isNotEmpty()
}
