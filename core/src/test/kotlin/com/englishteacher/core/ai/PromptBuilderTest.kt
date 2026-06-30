package com.englishteacher.core.ai

import com.englishteacher.core.catalog.TopicCatalog
import com.englishteacher.core.domain.model.ChatMessage
import com.englishteacher.core.domain.model.ConversationSession
import com.englishteacher.core.domain.model.FeedbackLanguage
import com.englishteacher.core.domain.model.ProficiencyLevel
import com.englishteacher.core.domain.model.Speaker
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class PromptBuilderTest {
    private val builder = PromptBuilder()

    private fun session(
        messages: List<ChatMessage> = emptyList(),
        level: ProficiencyLevel = ProficiencyLevel.INTERMEDIATE,
    ) = ConversationSession(
        id = "s1",
        topicId = "restaurant",
        title = "At a Restaurant",
        createdAtMillis = 0,
        updatedAtMillis = 0,
        proficiency = level,
        feedbackLanguage = FeedbackLanguage.CHINESE,
        messages = messages,
    )

    @Test
    fun `system prompt includes scenario and british english rules`() {
        val topic = TopicCatalog.byId("restaurant")!!
        val prompt = builder.systemPrompt(session(), topic)

        assertTrue(prompt.contains(topic.scenarioPrompt), "scenario must be embedded")
        assertTrue(prompt.contains("British English"), "must demand British English")
        assertTrue(prompt.contains("explanationEn") && prompt.contains("explanationZh"))
        assertTrue(prompt.contains("repeatTarget"))
    }

    @Test
    fun `proficiency changes the guidance`() {
        val topic = TopicCatalog.freeChat
        val beginner = builder.systemPrompt(session(level = ProficiencyLevel.BEGINNER), topic)
        val advanced = builder.systemPrompt(session(level = ProficiencyLevel.ADVANCED), topic)

        assertTrue(beginner.contains("BEGINNER"))
        assertTrue(advanced.contains("ADVANCED"))
    }

    @Test
    fun `messages map speakers to roles and append the new utterance`() {
        val history =
            listOf(
                ChatMessage("m1", Speaker.TUTOR, "Welcome!", 1),
                ChatMessage("m2", Speaker.USER, "Hello", 2),
            )
        val messages = builder.messages(session(history), "I would like the soup")

        assertEquals(3, messages.size)
        assertEquals("assistant", messages[0].role)
        assertEquals("user", messages[1].role)
        assertEquals("user", messages[2].role)
        assertEquals("I would like the soup", messages[2].content)
    }

    @Test
    fun `empty history seeds a kickoff user message`() {
        val messages = builder.messages(session(), null)
        assertEquals(1, messages.size)
        assertEquals("user", messages[0].role)
    }

    @Test
    fun `output schema is a well-formed object schema`() {
        val schema = PromptBuilder.OUTPUT_SCHEMA
        assertEquals("object", schema["type"]?.let { it.toString().trim('"') })
        val required = schema["required"]!!.jsonArray.map { it.toString().trim('"') }
        assertTrue(required.containsAll(listOf("reply", "hasErrors", "corrections", "repeatTarget")))

        val correctionProps =
            schema["properties"]!!.jsonObject["corrections"]!!.jsonObject["items"]!!
                .jsonObject["properties"]!!.jsonObject
        assertTrue(correctionProps.containsKey("explanationEn"))
        assertTrue(correctionProps.containsKey("explanationZh"))
    }
}
