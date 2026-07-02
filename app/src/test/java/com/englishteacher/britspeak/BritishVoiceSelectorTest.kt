package com.englishteacher.britspeak

import com.englishteacher.britspeak.speech.BritishVoiceSelector
import com.englishteacher.britspeak.speech.BritishVoiceSelector.Option
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class BritishVoiceSelectorTest {
    @Test
    fun `picks the highest-quality offline british voice`() {
        val chosen =
            BritishVoiceSelector.pick(
                listOf(
                    Option("gb-low", "en", "GB", quality = 300, needsNetwork = false),
                    Option("gb-high", "en", "GB", quality = 500, needsNetwork = false),
                    Option("us-high", "en", "US", quality = 500, needsNetwork = false),
                ),
            )
        assertEquals("gb-high", chosen)
    }

    @Test
    fun `prefers an offline voice over a higher-quality network one`() {
        val chosen =
            BritishVoiceSelector.pick(
                listOf(
                    Option("gb-network", "en", "GB", quality = 500, needsNetwork = true),
                    Option("gb-local", "en", "GB", quality = 400, needsNetwork = false),
                ),
            )
        assertEquals("gb-local", chosen)
    }

    @Test
    fun `falls back to a network british voice when no offline one exists`() {
        val chosen =
            BritishVoiceSelector.pick(
                listOf(Option("gb-network", "en", "GB", quality = 400, needsNetwork = true)),
            )
        assertEquals("gb-network", chosen)
    }

    @Test
    fun `returns null when there is no british voice at all`() {
        assertNull(
            BritishVoiceSelector.pick(
                listOf(
                    Option("us", "en", "US", quality = 500, needsNetwork = false),
                    Option("au", "en", "AU", quality = 500, needsNetwork = false),
                ),
            ),
        )
    }

    @Test
    fun `is case-insensitive on language and country codes`() {
        val chosen =
            BritishVoiceSelector.pick(
                listOf(Option("gb", "EN", "gb", quality = 300, needsNetwork = false)),
            )
        assertEquals("gb", chosen)
    }

    @Test
    fun `breaks quality ties by preferring lower latency then stable name`() {
        val chosen =
            BritishVoiceSelector.pick(
                listOf(
                    Option("b", "en", "GB", quality = 400, needsNetwork = false, isLatencyHigh = true),
                    Option("a", "en", "GB", quality = 400, needsNetwork = false, isLatencyHigh = false),
                ),
            )
        assertEquals("a", chosen)
    }
}
