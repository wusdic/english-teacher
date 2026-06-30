package com.englishteacher.britspeak.ui.avatar

/** The visual state of the digital-human tutor. */
enum class AvatarMood {
    /** At rest, gently breathing/blinking. */
    IDLE,

    /** Actively listening to the learner's microphone. */
    LISTENING,

    /** Waiting for the model's reply. */
    THINKING,

    /** Speaking via TTS (mouth animates). */
    SPEAKING,
}
