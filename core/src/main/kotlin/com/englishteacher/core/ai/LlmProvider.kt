package com.englishteacher.core.ai

/** The wire format used to talk to the language model. */
enum class LlmProvider {
    /** Anthropic Messages API (`/v1/messages`, `x-api-key`, structured outputs). */
    ANTHROPIC,

    /** OpenAI-compatible Chat Completions API (`/chat/completions`, `Bearer` auth, JSON mode). */
    OPENAI,
}

/**
 * Selects and configures the model backend. One config covers both wire formats so the app can
 * point at Anthropic, OpenAI, or any OpenAI-compatible endpoint (DeepSeek, Qwen, Kimi, a local
 * server, …) by changing [provider], [baseUrl] and [model].
 */
data class ProviderConfig(
    val provider: LlmProvider,
    val apiKey: String,
    val model: String,
    val baseUrl: String,
    val maxTokens: Int = 1024,
) {
    companion object {
        const val ANTHROPIC_BASE_URL = "https://api.anthropic.com"
        const val ANTHROPIC_DEFAULT_MODEL = "claude-opus-4-8"
        const val OPENAI_BASE_URL = "https://api.openai.com/v1"
        const val OPENAI_DEFAULT_MODEL = "gpt-4o"

        /** Reasonable defaults for a freshly-selected [provider]. */
        fun defaultsFor(provider: LlmProvider): ProviderConfig =
            when (provider) {
                LlmProvider.ANTHROPIC ->
                    ProviderConfig(provider, "", ANTHROPIC_DEFAULT_MODEL, ANTHROPIC_BASE_URL)
                LlmProvider.OPENAI ->
                    ProviderConfig(provider, "", OPENAI_DEFAULT_MODEL, OPENAI_BASE_URL)
            }
    }
}
