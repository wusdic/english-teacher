package com.englishteacher.britspeak.speech

/**
 * Pure energy-based endpointer for one spoken turn, extracted from [WhisperSpeechToText] so the
 * tuning-sensitive logic is unit-testable without Android audio APIs.
 *
 * Design goals from on-device testing:
 * - **Not fooled by background noise**: the noise floor is seeded from the first chunk and adapts
 *   upward on quiet chunks, so steady ambience (TV, fan, street) sets the baseline instead of a
 *   single silent lull pinning the floor near zero forever. Speech must be [noiseMultiplier]×
 *   louder than that baseline.
 * - **No false starts on transient spikes**: speech only starts after [startChunksRequired]
 *   consecutive loud chunks (~200 ms), so a door slam or keyboard click doesn't open a turn.
 * - **Fast finalisation**: once speech started, [trailingSilenceMs] below the threshold ends it.
 * - Speech itself never inflates the floor (only sub-threshold chunks adapt it), so a long
 *   sentence can't raise the bar mid-utterance and clip itself.
 */
class EnergyEndpointer(
    private val baseSpeechRms: Double = BASE_SPEECH_RMS,
    private val noiseMultiplier: Double = NOISE_MULTIPLIER,
    private val startChunksRequired: Int = START_CHUNKS_REQUIRED,
    private val trailingSilenceMs: Int = TRAILING_SILENCE_MS,
    private val maxUtteranceMs: Int = MAX_UTTERANCE_MS,
) {
    enum class Decision {
        /** Keep recording. */
        CONTINUE,

        /** Speech happened and has now ended — finalise the turn. */
        END_OF_SPEECH,

        /** The maximum window elapsed — finalise with whatever was captured. */
        TIMEOUT,
    }

    /** True once sustained speech has been detected in this turn. */
    var speechStarted: Boolean = false
        private set

    private var noiseFloor = Double.NaN
    private var consecutiveLoud = 0
    private var silenceMs = 0
    private var elapsedMs = 0

    /** Feeds one audio chunk's RMS (samples in [-1,1]) and its duration; returns what to do. */
    fun feed(
        rms: Double,
        chunkMs: Int,
    ): Decision {
        elapsedMs += chunkMs

        if (noiseFloor.isNaN()) {
            // First chunk calibrates the baseline; it can never itself count as speech.
            noiseFloor = rms
            return if (elapsedMs >= maxUtteranceMs) Decision.TIMEOUT else Decision.CONTINUE
        }

        val threshold = maxOf(baseSpeechRms, noiseFloor * noiseMultiplier)
        if (rms > threshold) {
            consecutiveLoud++
            if (!speechStarted && consecutiveLoud >= startChunksRequired) speechStarted = true
            if (speechStarted) silenceMs = 0
        } else {
            consecutiveLoud = 0
            // Only non-speech chunks adapt the floor: downward instantly, upward gently (~2% per
            // chunk) so rising ambience becomes the new baseline within a couple of seconds.
            noiseFloor = minOf(rms, noiseFloor * FLOOR_RISE_PER_CHUNK)
            if (speechStarted) {
                silenceMs += chunkMs
                if (silenceMs >= trailingSilenceMs) return Decision.END_OF_SPEECH
            }
        }

        return if (elapsedMs >= maxUtteranceMs) Decision.TIMEOUT else Decision.CONTINUE
    }

    companion object {
        /** Minimum RMS that can ever count as speech, even in a silent room. */
        const val BASE_SPEECH_RMS = 0.02

        /** Speech must be this many times louder than the adaptive noise floor. */
        const val NOISE_MULTIPLIER = 3.0

        /** Consecutive loud chunks (~100 ms each) required to open a turn. */
        const val START_CHUNKS_REQUIRED = 2

        /** Trailing silence that ends the turn. Tap the stop button to end it immediately. */
        const val TRAILING_SILENCE_MS = 600

        const val MAX_UTTERANCE_MS = 15_000

        /** Per-chunk upward creep of the noise floor while no speech is present. */
        const val FLOOR_RISE_PER_CHUNK = 1.02
    }
}
