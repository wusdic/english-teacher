package com.englishteacher.core.ai

import com.englishteacher.core.catalog.TopicCatalog
import com.englishteacher.core.domain.model.Persona
import com.englishteacher.core.domain.port.TutorEngine
import okhttp3.OkHttpClient

/** Builds a [TutorEngine] for the selected [ProviderConfig], sharing one [OkHttpClient]. */
object TutorEngineFactory {
    fun create(
        config: ProviderConfig,
        httpClient: OkHttpClient,
        catalog: TopicCatalog = TopicCatalog,
        persona: Persona = Persona.DEFAULT,
    ): TutorEngine =
        when (config.provider) {
            LlmProvider.ANTHROPIC ->
                AnthropicTutorEngine(
                    config =
                        AnthropicConfig(
                            apiKey = config.apiKey,
                            model = config.model,
                            baseUrl = config.baseUrl,
                            maxTokens = config.maxTokens,
                        ),
                    catalog = catalog,
                    persona = persona,
                    httpClient = httpClient,
                )
            LlmProvider.OPENAI ->
                OpenAiTutorEngine(
                    config = config,
                    catalog = catalog,
                    persona = persona,
                    httpClient = httpClient,
                )
        }
}
