package com.englishteacher.core.domain

import com.englishteacher.core.domain.model.ChatMessage
import com.englishteacher.core.domain.model.ConversationSession
import com.englishteacher.core.domain.model.Correction
import com.englishteacher.core.domain.model.CorrectionType
import com.englishteacher.core.domain.model.FeedbackLanguage
import com.englishteacher.core.domain.model.ProficiencyLevel
import com.englishteacher.core.domain.model.Speaker
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class ModelTest {
    @Test
    fun `correction returns explanation for chosen language`() {
        val c =
            Correction(
                original = "a",
                corrected = "b",
                type = CorrectionType.GRAMMAR,
                explanationEn = "english",
                explanationZh = "中文",
            )
        assertEquals("english", c.explanationFor(FeedbackLanguage.ENGLISH))
        assertEquals("中文", c.explanationFor(FeedbackLanguage.CHINESE))
    }

    @Test
    fun `session withMessage appends and advances timestamp`() {
        val base =
            ConversationSession(
                id = "s",
                topicId = "free_chat",
                title = "Free Chat",
                createdAtMillis = 0,
                updatedAtMillis = 0,
                proficiency = ProficiencyLevel.INTERMEDIATE,
                feedbackLanguage = FeedbackLanguage.CHINESE,
            )
        val updated = base.withMessage(ChatMessage("m1", Speaker.USER, "hi", 42))
        assertEquals(1, updated.messages.size)
        assertEquals(42, updated.updatedAtMillis)
    }

    @Test
    fun `topic requires openers`() {
        assertFailsWith<IllegalArgumentException> {
            com.englishteacher.core.domain.model.Topic(
                id = "x",
                title = "t",
                description = "d",
                category = com.englishteacher.core.domain.model.TopicCategory.EVERYDAY,
                scenarioPrompt = "s",
                sampleOpeners = emptyList(),
            )
        }
    }

    @Test
    fun `correction type lenient parse`() {
        assertEquals(CorrectionType.GRAMMAR, CorrectionType.fromWire("grammar"))
        assertEquals(CorrectionType.PRONUNCIATION, CorrectionType.fromWire("PRONUNCIATION"))
        assertEquals(CorrectionType.NATURALNESS, CorrectionType.fromWire(null))
        assertEquals(CorrectionType.NATURALNESS, CorrectionType.fromWire("bogus"))
        assertTrue(true)
    }
}
