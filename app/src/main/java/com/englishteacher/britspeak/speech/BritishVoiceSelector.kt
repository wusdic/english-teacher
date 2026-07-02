package com.englishteacher.britspeak.speech

/**
 * Chooses the most natural British English voice from the ones a TextToSpeech engine offers.
 * Pure (no Android APIs) so the ranking is unit-testable; [AndroidTutorVoice] adapts the engine's
 * `Voice` objects into [Option]s and applies the winner.
 *
 * Android often defaults to a serviceable-but-flat voice even when a higher-quality en-GB voice is
 * installed, so picking the best one explicitly makes the tutor sound noticeably more human.
 */
object BritishVoiceSelector {
    data class Option(
        val name: String,
        val language: String, // ISO-639, e.g. "en"
        val country: String, // ISO-3166, e.g. "GB"
        val quality: Int, // android.speech.tts.Voice quality (100..500, higher is better)
        val needsNetwork: Boolean,
        val isLatencyHigh: Boolean = false,
    )

    /**
     * Returns the name of the preferred en-GB voice, or null if none is available.
     *
     * Preference order: offline voices first (reliable when there's no connection), then highest
     * quality, then lower latency. Among truly equal candidates a stable name order breaks ties so
     * the choice is deterministic.
     */
    fun pick(options: List<Option>): String? {
        val british =
            options.filter {
                it.language.equals("en", ignoreCase = true) &&
                    it.country.equals("GB", ignoreCase = true)
            }
        if (british.isEmpty()) return null
        return british
            .sortedWith(
                compareByDescending<Option> { !it.needsNetwork }
                    .thenByDescending { it.quality }
                    .thenBy { it.isLatencyHigh }
                    .thenBy { it.name },
            )
            .first()
            .name
    }
}
