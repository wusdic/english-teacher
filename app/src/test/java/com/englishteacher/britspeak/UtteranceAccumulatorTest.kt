package com.englishteacher.britspeak

import com.englishteacher.britspeak.speech.UtteranceAccumulator
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class UtteranceAccumulatorTest {
    @Test
    fun `successive segments are joined with single spaces`() {
        val acc = UtteranceAccumulator()
        // Regression: a learner who pauses mid-sentence produces several segments that must be
        // stitched back into one utterance instead of only the first phrase being kept.
        acc.append("i would like")
        acc.append("a cup of coffee")
        assertEquals("i would like a cup of coffee", acc.combined())
    }

    @Test
    fun `blank and whitespace-only segments are ignored`() {
        val acc = UtteranceAccumulator()
        acc.append("hello")
        acc.append("   ")
        acc.append("")
        acc.append("there")
        assertEquals("hello there", acc.combined())
    }

    @Test
    fun `combined appends an in-progress partial without mutating the accumulated text`() {
        val acc = UtteranceAccumulator()
        acc.append("i want")
        assertEquals("i want a", acc.combined("a"))
        // The partial is not retained — the next call reflects only appended segments.
        assertEquals("i want", acc.combined())
    }

    @Test
    fun `combined with only a partial and no segments returns the partial`() {
        val acc = UtteranceAccumulator()
        assertTrue(acc.isEmpty())
        assertEquals("hello", acc.combined("hello"))
    }

    @Test
    fun `reset clears everything`() {
        val acc = UtteranceAccumulator()
        acc.append("something")
        acc.reset()
        assertTrue(acc.isEmpty())
        assertEquals("", acc.combined())
    }

    @Test
    fun `isEmpty reflects whether any segment was appended`() {
        val acc = UtteranceAccumulator()
        assertTrue(acc.isEmpty())
        acc.append("x")
        assertFalse(acc.isEmpty())
    }
}
