package com.englishteacher.core.usecase

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class SentenceChunkerTest {
    @Test
    fun `a single feed with two sentences splits both out`() {
        val chunker = SentenceChunker()
        val sentences = chunker.feed("Hello there! How are you?")
        assertEquals(listOf("Hello there!", "How are you?"), sentences)
        assertNull(chunker.flush())
    }

    @Test
    fun `sentences split across many small feeds are still detected correctly`() {
        val chunker = SentenceChunker()
        val out = mutableListOf<String>()
        for (chunk in "Lovely choice! Anything to drink?".map { it.toString() }) {
            out += chunker.feed(chunk)
        }
        assertEquals(listOf("Lovely choice!", "Anything to drink?"), out)
    }

    @Test
    fun `trailing text with no terminal punctuation is returned by flush`() {
        val chunker = SentenceChunker()
        val sentences = chunker.feed("Go on, I'm listening")
        assertEquals(emptyList(), sentences)
        assertEquals("Go on, I'm listening", chunker.flush())
    }

    @Test
    fun `flush after a fully-consumed buffer returns null`() {
        val chunker = SentenceChunker()
        chunker.feed("All done!")
        assertNull(chunker.flush())
    }

    @Test
    fun `flush clears the buffer so it cannot be double-emitted`() {
        val chunker = SentenceChunker()
        chunker.feed("Trailing bit")
        assertEquals("Trailing bit", chunker.flush())
        assertNull(chunker.flush())
    }

    @Test
    fun `chinese sentence-ending punctuation is recognised`() {
        val chunker = SentenceChunker()
        val sentences = chunker.feed("你好！今天怎么样？")
        assertEquals(listOf("你好！", "今天怎么样？"), sentences)
    }
}
