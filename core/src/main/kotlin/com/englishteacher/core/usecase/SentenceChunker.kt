package com.englishteacher.core.usecase

/**
 * Splits an incrementally-arriving text stream into whole sentences as soon as each one is
 * complete, so a caller (e.g. text-to-speech) can start speaking each sentence without waiting
 * for the entire reply to finish generating.
 *
 * Feed each new chunk via [feed]; call [flush] once the stream ends for any trailing text that
 * never reached a sentence-ending mark (the model's final sentence commonly has no trailing
 * punctuation left to trigger on until the stream itself ends).
 */
class SentenceChunker {
    private val buffer = StringBuilder()

    /** Feeds a new chunk of text; returns zero or more sentences newly completed by it. */
    fun feed(delta: String): List<String> {
        buffer.append(delta)
        val sentences = mutableListOf<String>()
        var start = 0
        var i = 0
        while (i < buffer.length) {
            if (buffer[i] in SENTENCE_ENDERS) {
                var end = i + 1
                while (end < buffer.length && buffer[end] in TRAILING_CHARS) end++
                val sentence = buffer.substring(start, end).trim()
                if (sentence.isNotEmpty()) sentences.add(sentence)
                start = end
                i = end
            } else {
                i++
            }
        }
        if (start > 0) buffer.delete(0, start)
        return sentences
    }

    /** Returns any buffered trailing text (and clears the buffer), or null if there is none. */
    fun flush(): String? {
        val trailing = buffer.toString().trim()
        buffer.clear()
        return trailing.ifEmpty { null }
    }

    companion object {
        private val SENTENCE_ENDERS = setOf('.', '!', '?', '。', '！', '？', '\n')
        private val TRAILING_CHARS = setOf('"', '\'', '”', '’', ')', ' ')
    }
}
