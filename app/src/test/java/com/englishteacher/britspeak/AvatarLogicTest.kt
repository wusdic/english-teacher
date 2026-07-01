package com.englishteacher.britspeak

import com.englishteacher.britspeak.ui.avatar.AvatarMood
import com.englishteacher.britspeak.ui.avatar.blinkOpenness
import com.englishteacher.britspeak.ui.chat.ChatPhase
import com.englishteacher.britspeak.ui.chat.avatarMoodFor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AvatarLogicTest {
    @Test
    fun `phase maps to the expected avatar mood`() {
        assertEquals(AvatarMood.IDLE, avatarMoodFor(ChatPhase.IDLE))
        assertEquals(AvatarMood.LISTENING, avatarMoodFor(ChatPhase.LISTENING))
        assertEquals(AvatarMood.LISTENING, avatarMoodFor(ChatPhase.REPEATING))
        assertEquals(AvatarMood.THINKING, avatarMoodFor(ChatPhase.THINKING))
        assertEquals(AvatarMood.SPEAKING, avatarMoodFor(ChatPhase.SPEAKING))
    }

    @Test
    fun `eyes are open for most of the blink cycle`() {
        assertEquals(1f, blinkOpenness(0.5f), 0.001f)
        assertEquals(1f, blinkOpenness(0.95f), 0.001f)
    }

    @Test
    fun `eyes close near the start of the blink window`() {
        // Mid-blink window should be near fully closed.
        assertTrue(blinkOpenness(0.03f) < 0.2f)
    }
}
