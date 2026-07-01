package com.englishteacher.core.domain.model

/**
 * The parsed result of one tutor response: a spoken [reply], any [corrections] for the
 * learner's previous utterance, and the [repeatTarget] sentence to practise.
 */
data class TutorTurn(
    val reply: String,
    val corrections: List<Correction>,
    val repeatTarget: String?,
) {
    val hasErrors: Boolean get() = corrections.isNotEmpty()
}
