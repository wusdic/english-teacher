package com.englishteacher.britspeak

import com.englishteacher.britspeak.speech.EnergyEndpointer
import com.englishteacher.britspeak.speech.EnergyEndpointer.Decision
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class EnergyEndpointerTest {
    private fun feedMany(
        ep: EnergyEndpointer,
        rms: Double,
        chunks: Int,
        chunkMs: Int = 100,
    ): Decision {
        var last = Decision.CONTINUE
        repeat(chunks) {
            last = ep.feed(rms, chunkMs)
            if (last != Decision.CONTINUE) return last
        }
        return last
    }

    @Test
    fun `speech in a quiet room starts after sustained loudness and ends after trailing silence`() {
        val ep = EnergyEndpointer()
        feedMany(ep, rms = 0.003, chunks = 5) // quiet ambience
        assertFalse(ep.speechStarted)

        feedMany(ep, rms = 0.1, chunks = 3) // talking
        assertTrue(ep.speechStarted)

        // 600 ms of silence finalises the turn.
        assertEquals(Decision.END_OF_SPEECH, feedMany(ep, rms = 0.003, chunks = 6))
    }

    @Test
    fun `steady background noise never opens a turn`() {
        // Regression: TV/fan noise used to be treated as speech because the floor was pinned at
        // the quietest chunk ever heard. A steady 0.03 ambience must calibrate, not trigger.
        val ep = EnergyEndpointer()
        assertEquals(Decision.CONTINUE, feedMany(ep, rms = 0.03, chunks = 50))
        assertFalse(ep.speechStarted)
    }

    @Test
    fun `speech clearly above background noise still starts`() {
        val ep = EnergyEndpointer()
        feedMany(ep, rms = 0.03, chunks = 10) // ambience calibrates the floor
        feedMany(ep, rms = 0.15, chunks = 3) // close-mic speech, well above 3x floor
        assertTrue(ep.speechStarted)
    }

    @Test
    fun `a single transient spike does not open a turn`() {
        val ep = EnergyEndpointer()
        feedMany(ep, rms = 0.003, chunks = 5)
        ep.feed(0.3, 100) // door slam: one loud chunk only
        assertFalse(ep.speechStarted)
        feedMany(ep, rms = 0.003, chunks = 3)
        assertFalse(ep.speechStarted)
    }

    @Test
    fun `a long sentence does not raise the bar against itself`() {
        val ep = EnergyEndpointer()
        feedMany(ep, rms = 0.003, chunks = 3)
        // 8 seconds of continuous speech...
        assertEquals(Decision.CONTINUE, feedMany(ep, rms = 0.1, chunks = 80))
        assertTrue(ep.speechStarted)
        // ...still finalises promptly once the speaker stops.
        assertEquals(Decision.END_OF_SPEECH, feedMany(ep, rms = 0.003, chunks = 6))
    }

    @Test
    fun `rising ambience becomes the new baseline within a couple of seconds`() {
        val ep = EnergyEndpointer()
        feedMany(ep, rms = 0.002, chunks = 5) // very quiet start
        // Fan turns on at 0.018 (just below the 0.02 base threshold): must never start speech,
        // and the floor should creep up to it.
        assertEquals(Decision.CONTINUE, feedMany(ep, rms = 0.018, chunks = 40))
        assertFalse(ep.speechStarted)
    }

    @Test
    fun `times out at the maximum window even if never silent`() {
        val ep = EnergyEndpointer(maxUtteranceMs = 1_000)
        assertEquals(Decision.TIMEOUT, feedMany(ep, rms = 0.1, chunks = 20))
    }
}
