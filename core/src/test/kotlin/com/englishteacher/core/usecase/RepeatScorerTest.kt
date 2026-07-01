package com.englishteacher.core.usecase

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class RepeatScorerTest {
    private val scorer = RepeatScorer()

    @Test
    fun `exact match scores 100 and passes`() {
        val result = scorer.score("I had breakfast at eight", "I had breakfast at eight")
        assertEquals(100, result.score)
        assertTrue(result.passed)
        assertTrue(result.matchedExpected.all { it })
    }

    @Test
    fun `case and punctuation are ignored`() {
        val result = scorer.score("I had breakfast.", "i HAD breakfast")
        assertEquals(100, result.score)
    }

    @Test
    fun `a missed word lowers the score and flags it`() {
        val result = scorer.score("I had breakfast at eight", "I had at eight")
        assertTrue(result.score < 100)
        // "breakfast" is the 3rd expected token and should be unmatched.
        assertFalse(result.matchedExpected[2])
    }

    @Test
    fun `completely different speech scores low and fails`() {
        val result = scorer.score("I had breakfast at eight", "the weather is nice today")
        assertFalse(result.passed)
        assertTrue(result.score <= 20)
    }

    @Test
    fun `extra words are penalised`() {
        val full = scorer.score("I had breakfast", "I had breakfast")
        val padded = scorer.score("I had breakfast", "I had breakfast um you know like")
        assertTrue(padded.score < full.score)
    }

    @Test
    fun `empty target with empty speech is full marks`() {
        assertEquals(100, scorer.score("", "").score)
    }

    @Test
    fun `word order matters`() {
        val correct = scorer.score("the cat sat", "the cat sat")
        val scrambled = scorer.score("the cat sat", "sat cat the")
        assertTrue(scrambled.score < correct.score)
    }
}
