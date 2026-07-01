package com.englishteacher.britspeak.speech

/**
 * Short acknowledgement phrases spoken the instant the learner finishes talking, masking the
 * network round-trip before the real (streamed) reply starts arriving — the same "let them know
 * you're listening" trick real-time voice assistants use to feel responsive.
 */
object FillerPhrases {
    private val phrases =
        listOf(
            "Mmhm.",
            "I see.",
            "Right.",
            "Okay!",
            "Go on.",
            "Lovely.",
            "Ah, I see.",
            "Interesting!",
        )

    fun random(): String = phrases.random()
}
