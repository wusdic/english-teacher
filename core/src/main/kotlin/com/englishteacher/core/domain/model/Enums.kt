package com.englishteacher.core.domain.model

/** Which language correction explanations are shown in. */
enum class FeedbackLanguage {
    ENGLISH,
    CHINESE,
}

/** Learner's self-declared (or inferred) proficiency, used to calibrate the tutor. */
enum class ProficiencyLevel {
    BEGINNER,
    INTERMEDIATE,
    ADVANCED,
}

/** The kind of mistake a [Correction] addresses. */
enum class CorrectionType {
    GRAMMAR,
    VOCABULARY,
    PRONUNCIATION,
    NATURALNESS,
    ;

    companion object {
        /** Lenient parse used when reading model output; defaults to [NATURALNESS]. */
        fun fromWire(value: String?): CorrectionType =
            entries.firstOrNull { it.name.equals(value?.trim(), ignoreCase = true) }
                ?: NATURALNESS
    }
}

/** Who produced a [ChatMessage]. */
enum class Speaker {
    USER,
    TUTOR,
}
