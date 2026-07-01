package com.englishteacher.britspeak

import com.englishteacher.britspeak.data.db.SessionMappers
import com.englishteacher.core.domain.model.ChatMessage
import com.englishteacher.core.domain.model.ConversationSession
import com.englishteacher.core.domain.model.Correction
import com.englishteacher.core.domain.model.CorrectionType
import com.englishteacher.core.domain.model.FeedbackLanguage
import com.englishteacher.core.domain.model.ProficiencyLevel
import com.englishteacher.core.domain.model.Speaker
import org.junit.Assert.assertEquals
import org.junit.Test

class SessionMapperTest {
    @Test
    fun `session round-trips through the entity mapping`() {
        val session =
            ConversationSession(
                id = "s1",
                topicId = "restaurant",
                title = "At a Restaurant",
                createdAtMillis = 100,
                updatedAtMillis = 200,
                proficiency = ProficiencyLevel.ADVANCED,
                feedbackLanguage = FeedbackLanguage.ENGLISH,
                messages =
                    listOf(
                        ChatMessage("m1", Speaker.TUTOR, "Welcome!", 100),
                        ChatMessage(
                            id = "m2",
                            speaker = Speaker.USER,
                            text = "I want soup",
                            timestampMillis = 150,
                        ),
                        ChatMessage(
                            id = "m3",
                            speaker = Speaker.TUTOR,
                            text = "Lovely choice!",
                            timestampMillis = 200,
                            corrections =
                                listOf(
                                    Correction(
                                        original = "I want soup",
                                        corrected = "I'd like the soup, please",
                                        type = CorrectionType.NATURALNESS,
                                        explanationEn = "More polite.",
                                        explanationZh = "更礼貌。",
                                    ),
                                ),
                            repeatTarget = "I'd like the soup, please.",
                        ),
                    ),
            )

        val restored = SessionMappers.toDomain(SessionMappers.toEntity(session))

        assertEquals(session, restored)
        assertEquals(1, restored.messages[2].corrections.size)
        assertEquals("I'd like the soup, please.", restored.messages[2].repeatTarget)
        assertEquals(FeedbackLanguage.ENGLISH, restored.feedbackLanguage)
        assertEquals(ProficiencyLevel.ADVANCED, restored.proficiency)
    }

    @Test
    fun `custom scenario prompt round-trips through the entity mapping`() {
        val session =
            ConversationSession(
                id = "s2",
                topicId = "custom",
                title = "My Own Scenario",
                createdAtMillis = 100,
                updatedAtMillis = 100,
                proficiency = ProficiencyLevel.BEGINNER,
                feedbackLanguage = FeedbackLanguage.CHINESE,
                messages = listOf(ChatMessage("m1", Speaker.TUTOR, "Hello!", 100)),
                customScenarioPrompt = "You are a dragon guarding a library.",
            )

        val restored = SessionMappers.toDomain(SessionMappers.toEntity(session))

        assertEquals(session, restored)
        assertEquals("You are a dragon guarding a library.", restored.customScenarioPrompt)
    }
}
