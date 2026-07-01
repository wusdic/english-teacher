package com.englishteacher.core.domain.model

/**
 * A single correction the tutor offers for something the learner said.
 *
 * Both [explanationEn] and [explanationZh] are always populated by the tutor so the UI can
 * switch the displayed language instantly without another model call.
 */
data class Correction(
    val original: String,
    val corrected: String,
    val type: CorrectionType,
    val explanationEn: String,
    val explanationZh: String,
) {
    /** Returns the explanation in the learner's chosen [language]. */
    fun explanationFor(language: FeedbackLanguage): String =
        when (language) {
            FeedbackLanguage.ENGLISH -> explanationEn
            FeedbackLanguage.CHINESE -> explanationZh
        }
}
