package com.englishteacher.core.ai

import com.englishteacher.core.domain.model.ConversationSession
import com.englishteacher.core.domain.model.FeedbackLanguage
import com.englishteacher.core.domain.model.ProficiencyLevel
import okhttp3.OkHttpClient
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ProviderTest {
    @Test
    fun `default config for anthropic`() {
        val c = ProviderConfig.defaultsFor(LlmProvider.ANTHROPIC)
        assertEquals(LlmProvider.ANTHROPIC, c.provider)
        assertEquals(ProviderConfig.ANTHROPIC_BASE_URL, c.baseUrl)
        assertEquals(ProviderConfig.ANTHROPIC_DEFAULT_MODEL, c.model)
    }

    @Test
    fun `default config for openai`() {
        val c = ProviderConfig.defaultsFor(LlmProvider.OPENAI)
        assertEquals(LlmProvider.OPENAI, c.provider)
        assertEquals(ProviderConfig.OPENAI_BASE_URL, c.baseUrl)
        assertEquals(ProviderConfig.OPENAI_DEFAULT_MODEL, c.model)
    }

    @Test
    fun `factory builds the anthropic engine for the anthropic provider`() {
        val engine =
            TutorEngineFactory.create(
                ProviderConfig.defaultsFor(LlmProvider.ANTHROPIC).copy(apiKey = "k"),
                OkHttpClient(),
            )
        assertTrue(engine is AnthropicTutorEngine)
    }

    @Test
    fun `factory builds the openai engine for the openai provider`() {
        val engine =
            TutorEngineFactory.create(
                ProviderConfig.defaultsFor(LlmProvider.OPENAI).copy(apiKey = "k"),
                OkHttpClient(),
            )
        assertTrue(engine is OpenAiTutorEngine)
    }
}
