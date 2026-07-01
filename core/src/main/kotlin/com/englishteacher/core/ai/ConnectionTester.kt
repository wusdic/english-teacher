package com.englishteacher.core.ai

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.addJsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException

/** Outcome of a "test my configuration" probe. */
sealed interface ConnectionTestResult {
    /** Auth + endpoint + model all worked; [sample] is a short reply from the model. */
    data class Success(val sample: String) : ConnectionTestResult

    /** Something failed; [message] is a human-readable reason. */
    data class Failure(val message: String) : ConnectionTestResult
}

/**
 * Sends one tiny request to validate a [ProviderConfig] end to end: that the API key is accepted,
 * the endpoint is reachable, and the model id is valid. Works for both the Anthropic and
 * OpenAI-compatible formats. Pure JVM (OkHttp) so it is unit-testable against a MockWebServer.
 */
class ConnectionTester(
    private val httpClient: OkHttpClient = defaultClient(),
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) {
    private val json = Json { ignoreUnknownKeys = true }

    suspend fun test(config: ProviderConfig): ConnectionTestResult {
        if (config.apiKey.isBlank()) return ConnectionTestResult.Failure("尚未填写 API key")
        if (config.model.isBlank()) return ConnectionTestResult.Failure("尚未填写模型名称")
        return when (config.provider) {
            LlmProvider.ANTHROPIC -> testAnthropic(config)
            LlmProvider.OPENAI -> testOpenAi(config)
        }
    }

    private suspend fun testAnthropic(config: ProviderConfig): ConnectionTestResult {
        val body =
            buildJsonObject {
                put("model", config.model)
                put("max_tokens", 16)
                putJsonArray("messages") {
                    addJsonObject {
                        put("role", "user")
                        put("content", PROBE)
                    }
                }
            }.toString()

        val request =
            Request.Builder()
                .url("${config.baseUrl.trimEnd('/')}/v1/messages")
                .addHeader("x-api-key", config.apiKey)
                .addHeader("anthropic-version", AnthropicConfig.DEFAULT_VERSION)
                .addHeader("content-type", "application/json")
                .post(body.toRequestBody(JSON_MEDIA_TYPE))
                .build()

        return execute(request) { raw ->
            val wire = json.decodeFromString(WireResponse.serializer(), raw)
            wire.content.firstOrNull { it.type == "text" }?.text?.trim().orEmpty()
        }
    }

    private suspend fun testOpenAi(config: ProviderConfig): ConnectionTestResult {
        val body =
            buildJsonObject {
                put("model", config.model)
                put("max_tokens", 16)
                putJsonArray("messages") {
                    addJsonObject {
                        put("role", "user")
                        put("content", PROBE)
                    }
                }
            }.toString()

        val request =
            Request.Builder()
                .url("${config.baseUrl.trimEnd('/')}/chat/completions")
                .addHeader("Authorization", "Bearer ${config.apiKey}")
                .addHeader("content-type", "application/json")
                .post(body.toRequestBody(JSON_MEDIA_TYPE))
                .build()

        return execute(request) { raw ->
            val wire = json.decodeFromString(OpenAiResponse.serializer(), raw)
            wire.choices.firstOrNull()?.message?.content?.trim().orEmpty()
        }
    }

    private suspend fun execute(
        request: Request,
        extractSample: (String) -> String,
    ): ConnectionTestResult =
        withContext(ioDispatcher) {
            try {
                httpClient.newCall(request).execute().use { response ->
                    val text = response.body?.string().orEmpty()
                    if (!response.isSuccessful) {
                        ConnectionTestResult.Failure("HTTP ${response.code}: ${extractError(text)}")
                    } else {
                        val sample =
                            runCatching { extractSample(text) }.getOrDefault("").ifBlank { "OK" }
                        ConnectionTestResult.Success(sample.take(120))
                    }
                }
            } catch (e: IOException) {
                ConnectionTestResult.Failure("网络错误：${e.message ?: "无法连接"}")
            } catch (t: Throwable) {
                ConnectionTestResult.Failure(t.message ?: "未知错误")
            }
        }

    private fun extractError(bodyText: String): String {
        // Try Anthropic then OpenAI error envelopes, else raw text.
        runCatching {
            json.decodeFromString(WireErrorEnvelope.serializer(), bodyText).error?.message
        }.getOrNull()?.let { return it }
        runCatching {
            json.decodeFromString(OpenAiErrorEnvelope.serializer(), bodyText).error?.message
        }.getOrNull()?.let { return it }
        return bodyText.take(160)
    }

    companion object {
        private const val PROBE = "Reply with just the word: OK"
        private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()

        fun defaultClient(): OkHttpClient =
            OkHttpClient.Builder()
                .callTimeout(java.time.Duration.ofSeconds(30))
                .build()
    }
}
