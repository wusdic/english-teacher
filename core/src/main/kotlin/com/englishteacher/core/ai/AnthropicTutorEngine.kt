package com.englishteacher.core.ai

import com.englishteacher.core.catalog.TopicCatalog
import com.englishteacher.core.domain.model.ConversationSession
import com.englishteacher.core.domain.model.Persona
import com.englishteacher.core.domain.model.Topic
import com.englishteacher.core.domain.model.TutorTurn
import com.englishteacher.core.domain.port.TutorEngine
import com.englishteacher.core.domain.port.TutorEngineException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException

/**
 * [TutorEngine] backed by the Anthropic Messages API.
 *
 * The engine is pure JVM (OkHttp + kotlinx-serialization) so it is fully exercised in tests
 * against an in-process `MockWebServer`.
 */
class AnthropicTutorEngine(
    private val config: AnthropicConfig,
    private val catalog: TopicCatalog = TopicCatalog,
    private val persona: Persona = Persona.DEFAULT,
    private val promptBuilder: PromptBuilder = PromptBuilder(persona),
    private val parser: ConversationResponseParser = ConversationResponseParser(),
    private val httpClient: OkHttpClient = defaultClient(),
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) : TutorEngine {
    private val json = Json { encodeDefaults = true }
    private val envelopeJson = Json { ignoreUnknownKeys = true }

    override suspend fun respond(
        session: ConversationSession,
        userUtterance: String,
    ): TutorTurn = call(session, userUtterance)

    override suspend fun opener(session: ConversationSession): TutorTurn = call(session, null)

    private suspend fun call(
        session: ConversationSession,
        userUtterance: String?,
    ): TutorTurn {
        val topic = resolveTopic(session)
        val request =
            WireRequest(
                model = config.model,
                maxTokens = config.maxTokens,
                system = promptBuilder.systemPrompt(session, topic),
                messages = promptBuilder.messages(session, userUtterance),
                outputConfig =
                    WireOutputConfig(
                        format = WireOutputFormat(schema = PromptBuilder.OUTPUT_SCHEMA),
                    ),
            )

        val bodyJson = json.encodeToString(WireRequest.serializer(), request)
        val httpRequest =
            Request.Builder()
                .url("${config.baseUrl.trimEnd('/')}/v1/messages")
                .addHeader("x-api-key", config.apiKey)
                .addHeader("anthropic-version", config.anthropicVersion)
                .addHeader("content-type", "application/json")
                .post(bodyJson.toRequestBody(JSON_MEDIA_TYPE))
                .build()

        val rawResponse =
            withContext(ioDispatcher) {
                try {
                    httpClient.newCall(httpRequest).execute().use { response ->
                        val text = response.body?.string().orEmpty()
                        if (!response.isSuccessful) {
                            throw TutorEngineException(
                                "Anthropic API error ${response.code}: ${extractError(text)}",
                            )
                        }
                        text
                    }
                } catch (e: IOException) {
                    throw TutorEngineException("Network error contacting Anthropic API", e)
                }
            }

        val wire =
            try {
                envelopeJson.decodeFromString(WireResponse.serializer(), rawResponse)
            } catch (t: Throwable) {
                throw TutorEngineException("Malformed Anthropic API response envelope", t)
            }

        val structured =
            wire.content
                .filter { it.type == "text" }
                .mapNotNull { it.text }
                .joinToString(separator = "")
                .ifBlank { throw TutorEngineException("Anthropic API returned no text content") }

        return parser.parse(structured)
    }

    /**
     * Resolves the topic used to build the system prompt. A session's own
     * [ConversationSession.customScenarioPrompt] (set for every session, including a
     * user-authored custom scenario) always wins over the static catalog, so a scenario that
     * isn't in [catalog] at all — or has since changed there — still renders correctly.
     */
    private fun resolveTopic(session: ConversationSession): Topic {
        val base = catalog.byId(session.topicId) ?: catalog.freeChat
        val custom = session.customScenarioPrompt?.trim()
        return if (!custom.isNullOrBlank()) base.copy(scenarioPrompt = custom) else base
    }

    private fun extractError(body: String): String =
        try {
            envelopeJson
                .decodeFromString(WireErrorEnvelope.serializer(), body)
                .error
                ?.message
                ?: body.take(200)
        } catch (_: Throwable) {
            body.take(200)
        }

    companion object {
        private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()

        fun defaultClient(): OkHttpClient =
            OkHttpClient.Builder()
                .callTimeout(java.time.Duration.ofSeconds(60))
                .build()
    }
}
