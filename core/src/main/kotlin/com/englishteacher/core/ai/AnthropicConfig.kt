package com.englishteacher.core.ai

/**
 * Connection settings for the Anthropic Messages API.
 *
 * [baseUrl] is overridable so the same engine can talk to a self-hosted backend proxy
 * (recommended for production key custody) instead of the public API.
 */
data class AnthropicConfig(
    val apiKey: String,
    val model: String = DEFAULT_MODEL,
    val baseUrl: String = DEFAULT_BASE_URL,
    val anthropicVersion: String = DEFAULT_VERSION,
    val maxTokens: Int = 1024,
) {
    companion object {
        const val DEFAULT_MODEL = "claude-opus-4-8"
        const val DEFAULT_BASE_URL = "https://api.anthropic.com"
        const val DEFAULT_VERSION = "2023-06-01"
    }
}
