package com.englishteacher.core.ai

/**
 * Consumes a model's reply text as it streams in, chunk by chunk, and separates it into three
 * things without ever emitting a partial marker to the caller:
 *
 * 1. The spoken reply text (safe to speak/display the moment it's confirmed).
 * 2. Hidden reasoning (`<think>`/`<thinking>`/`<reasoning>` blocks), which is discarded — the
 *    learner must never see a model's chain-of-thought.
 * 3. The JSON tail that follows [DELIMITER] (corrections/repeat target), buffered verbatim for
 *    [finishReplyAndJson] to parse once the stream ends.
 *
 * A marker split across two chunks (e.g. `"<thi"` then `"nk>"`) is handled by holding back the
 * longest unconfirmed suffix that could still be the start of a marker, so nothing leaks.
 */
class StreamingReplyAccumulator {
    private enum class Mode { REPLY, THINK, JSON }

    private var mode = Mode.REPLY
    private var thinkCloseTag: String? = null
    private val pending = StringBuilder()
    private val replyOut = StringBuilder()
    private val jsonOut = StringBuilder()

    /** Feeds a new chunk of raw text; returns the (possibly empty) newly-confirmed reply text. */
    fun feed(delta: String): String {
        if (mode == Mode.JSON) {
            jsonOut.append(delta)
            return ""
        }
        pending.append(delta)
        val emitted = StringBuilder()
        var progress = true
        while (progress) {
            progress = false
            when (mode) {
                Mode.REPLY -> progress = advanceReply(emitted)
                Mode.THINK -> progress = advanceThink()
                // Reached only right after advanceReply() flips mode mid-loop on this same feed()
                // call; the JSON tail for that transition is already drained there, so no-op.
                Mode.JSON -> {}
            }
        }
        return emitted.toString()
    }

    /** Call once the stream has ended; returns the final trimmed (reply, jsonTail) pair. */
    fun finishReplyAndJson(): Pair<String, String> {
        if (mode == Mode.REPLY && pending.isNotEmpty()) {
            replyOut.append(pending)
            pending.clear()
        }
        return replyOut.toString().trim() to jsonOut.toString().trim()
    }

    private fun advanceReply(emitted: StringBuilder): Boolean {
        val match = earliestMarker(pending)
        if (match != null) {
            val (index, marker) = match
            if (index > 0) {
                val safe = pending.substring(0, index)
                emitted.append(safe)
                replyOut.append(safe)
            }
            pending.delete(0, index + marker.length)
            if (marker.equals(DELIMITER, ignoreCase = true)) {
                mode = Mode.JSON
                jsonOut.append(pending)
                pending.clear()
            } else {
                mode = Mode.THINK
                thinkCloseTag = "</" + marker.removePrefix("<")
            }
            return true
        }
        // No full marker yet — emit everything except a tail that could still become one.
        val unsafe = unsafeTailLength(pending)
        if (pending.length > unsafe) {
            val safe = pending.substring(0, pending.length - unsafe)
            emitted.append(safe)
            replyOut.append(safe)
            pending.delete(0, pending.length - unsafe)
        }
        return false
    }

    private fun advanceThink(): Boolean {
        val closeTag = thinkCloseTag ?: return false
        val idx = indexOfIgnoreCase(pending, closeTag)
        if (idx < 0) return false
        pending.delete(0, idx + closeTag.length)
        mode = Mode.REPLY
        thinkCloseTag = null
        return true
    }

    /** Returns (index, matchedMarker) of whichever watched marker occurs earliest in [text]. */
    private fun earliestMarker(text: CharSequence): Pair<Int, String>? =
        MARKERS
            .mapNotNull { marker ->
                val idx = indexOfIgnoreCase(text, marker)
                if (idx >= 0) idx to marker else null
            }
            .minByOrNull { it.first }

    /** Longest suffix of [text] that could still be the start of an in-flight marker. */
    private fun unsafeTailLength(text: CharSequence): Int {
        val limit = minOf(MAX_MARKER_LEN - 1, text.length)
        for (len in limit downTo 1) {
            val suffix = text.subSequence(text.length - len, text.length)
            if (MARKERS.any { it.startsWith(suffix, ignoreCase = true) }) return len
        }
        return 0
    }

    private fun indexOfIgnoreCase(
        haystack: CharSequence,
        needle: String,
    ): Int = haystack.toString().indexOf(needle, ignoreCase = true)

    companion object {
        /** Marks the end of the spoken reply and the start of the corrections JSON. */
        const val DELIMITER = "###JSON###"

        private val THINK_OPEN_TAGS = listOf("<think>", "<thinking>", "<reasoning>")
        private val MARKERS = THINK_OPEN_TAGS + DELIMITER
        private val MAX_MARKER_LEN = MARKERS.maxOf { it.length }
    }
}
