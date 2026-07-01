package com.englishteacher.britspeak

import com.englishteacher.britspeak.ui.avatar.AvatarMood
import com.englishteacher.britspeak.ui.chat.ChatPhase
import com.englishteacher.britspeak.ui.chat.ChatUiState
import com.englishteacher.core.domain.model.ChatMessage
import com.englishteacher.core.domain.model.ConversationSession
import com.englishteacher.core.domain.model.FeedbackLanguage
import com.englishteacher.core.domain.model.ProficiencyLevel
import com.englishteacher.core.domain.model.Speaker
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ChatContractsTest {
    private fun sessionWith(vararg messages: ChatMessage) =
        ConversationSession(
            id = "s",
            topicId = "free_chat",
            title = "Free Chat",
            createdAtMillis = 0,
            updatedAtMillis = 0,
            proficiency = ProficiencyLevel.INTERMEDIATE,
            feedbackLanguage = FeedbackLanguage.CHINESE,
            messages = messages.toList(),
        )

    @Test
    fun `subtitle shows tutor reply while speaking`() {
        val state =
            ChatUiState(
                session = sessionWith(ChatMessage("m1", Speaker.TUTOR, "Lovely to see you!", 1)),
                phase = ChatPhase.SPEAKING,
            )
        assertEquals("Lovely to see you!", state.subtitle)
        assertEquals(AvatarMood.SPEAKING, state.avatarMood)
    }

    @Test
    fun `subtitle shows the partial transcript while listening`() {
        val state = ChatUiState(phase = ChatPhase.LISTENING, partialTranscript = "i would like")
        assertEquals("i would like", state.subtitle)
        assertEquals(AvatarMood.LISTENING, state.avatarMood)
    }

    @Test
    fun `subtitle is empty when idle`() {
        assertEquals("", ChatUiState(phase = ChatPhase.IDLE).subtitle)
    }

    @Test
    fun `canSpeak requires an api key and a non-busy phase`() {
        assertFalse(ChatUiState(hasApiKey = false, phase = ChatPhase.IDLE).canSpeak)
        assertTrue(ChatUiState(hasApiKey = true, phase = ChatPhase.IDLE).canSpeak)
        assertFalse(ChatUiState(hasApiKey = true, phase = ChatPhase.THINKING).canSpeak)
        assertFalse(ChatUiState(hasApiKey = true, phase = ChatPhase.SPEAKING).canSpeak)
    }
}
