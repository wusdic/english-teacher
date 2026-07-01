package com.englishteacher.britspeak.speech

/**
 * Joins the successive finalized segments Vosk emits for one spoken turn into a single utterance,
 * so a learner who pauses mid-sentence isn't cut off after the first phrase. Pure (no Android
 * APIs) so the joining logic is unit-testable; the silence/timeout handling lives in
 * [VoskSpeechToText].
 */
class UtteranceAccumulator {
    private val sb = StringBuilder()

    fun reset() {
        sb.setLength(0)
    }

    /** Appends a finalized segment, ignoring blanks and inserting a single separating space. */
    fun append(segment: String) {
        val s = segment.trim()
        if (s.isEmpty()) return
        if (sb.isNotEmpty()) sb.append(' ')
        sb.append(s)
    }

    /** The accumulated text so far, optionally plus an in-progress [partial], trimmed. */
    fun combined(partial: String? = null): String {
        val out = StringBuilder(sb)
        val p = partial?.trim()
        if (!p.isNullOrEmpty()) {
            if (out.isNotEmpty()) out.append(' ')
            out.append(p)
        }
        return out.toString().trim()
    }

    fun isEmpty(): Boolean = sb.isEmpty()
}
