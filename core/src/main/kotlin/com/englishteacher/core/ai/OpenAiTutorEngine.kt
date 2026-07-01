package com.englishteacher.core.ai

import com.englishteacher.core.catalog.TopicCatalog
import com.englishteacher.core.domain.model.ConversationSession
import com.englishteacher.core.domain.model.Persona
import com.englishteacher.core.domain.model.Speaker
import com.englishteacher.core.domain.model.Topic
import com.englishteacher.core.domain.model.TutorTurn
import com.englishteacher.core.domain.port.TutorEngine
import com.englishteacher.core.domain.port.TutorEngineException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException

/**
 * [TutorEngine] for the OpenAI-compatible Chat Completions API. Works with OpenAI and any
 * OpenAI-compatible endpoint (DeepSeek, Qwen, Kimi, local servers, …) by pointing [ProviderConfig]
 * at the right `baseUrl`/`model`. Uses JSON mode plus an explicit JSON-shape instruction, then
 * reuses [ConversationResponseParser] to decode the reply.
 */
class OpenAiTutorEngine(
    private val config: ProviderConfig,
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
        val topic = resolveTopic(session.topicId)
        val systemContent =
            promptBuilder.systemPrompt(session, topic) + "\n\n" + PromptBuilder.JSON_INSTRUCTION

        val messages = buildList {
            add(OpenAiMessage(role = "system", content = systemContent))
            session.messages.forEach { m ->
                add(
                    OpenAiMessage(
                        role = if (m.speaker == Speaker.USER) "user" else "assistant",
                        content = m.text,
                    ),
                )
            }
            if (userUtterance != null) {
                add(OpenAiMessage(role = "user", content = userUtterance))
            }
            // Chat Completions requires at least one non-system message.
            if (none { it.role != "system" }) {
                add(OpenAiMessage(role = "user", content = "Let's begin our conversation."))
            }
        }

        val request =
            OpenAiRequest(
                model = config.model,
                messages = messages,
                maxTokens = config.maxTokens,
                responseFormat = OpenAiResponseFormat(type = "json_object"),
            )

        val bodyJson = json.encodeToString(OpenAiRequest.serializer(), request)
        val httpRequest =
            Request.Builder()
                .url("${config.baseUrl.trimEnd('/')}/chat/completions")
                .addHeader("Authorization", "Bearer ${config.apiKey}")
                .addHeader("content-type", "application/json")
                .post(bodyJson.toRequestBody(JSON_MEDIA_TYPE))
                .build()

        val raw =
            withContext(ioDispatcher) {
                try {
                    httpClient.newCall(httpRequest).execute().use { response ->
                        val text = response.body?.string().orEmpty()
                        if (!response.isSuccessful) {
                            throw TutorEngineException(
                                "OpenAI API error ${response.code}: ${extractError(text)}",
                            )
                        }
                        text
                    }
                } catch (e: IOException) {
                    throw TutorEngineException("Network error contacting OpenAI-compatible API", e)
                }
            }

        val wire =
            try {
                envelopeJson.decodeFromString(OpenAiResponse.serializer(), raw)
            } catch (t: Throwable) {
                throw TutorEngineException("Malformed OpenAI API response envelope", t)
            }

        val content =
            wire.choices.firstOrNull()?.message?.content
                ?.let { stripCodeFence(it) }
                ?.takeIf { it.isNotBlank() }
                ?: throw TutorEngineException("OpenAI API returned no message content")

        return parser.parse(content)
    }

    private fun resolveTopic(topicId: String): Topic =
        catalog.byId(topicId) ?: catalog.freeChat

    private fun extractError(body: String): String =
        try {
            envelopeJson.decodeFromString(OpenAiErrorEnvelope.serializer(), body).error?.message
                ?: body.take(200)
        } catch (_: Throwable) {
            body.take(200)
        }

    /** Some models wrap JSON in ```json fences despite JSON mode; strip them defensively. */
    private fun stripCodeFence(text: String): String {
        val trimmed = text.trim()
        if (!trimmed.startsWith("```")) return trimmed
        return trimmed
            .removePrefix("```json")
            .removePrefix("```JSON")
            .removePrefix("```")
            .removeSuffix("```")
            .trim()
    }

    companion object {
        private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()

        fun defaultClient(): OkHttpClient =
            OkHttpClient.Builder()
                .callTimeout(java.time.Duration.ofSeconds(60))
                .build()
    }
}

@Serializable
internal data class OpenAiMessage(
    val role: String,
    val content: String,
)

@Serializable
internal data class OpenAiResponseFormat(
    val type: String,
)

@Serializable
internal data class OpenAiRequest(
    val model: String,
    val messages: List<OpenAiMessage>,
    @SerialName("max_tokens") val maxTokens: Int,
    @SerialName("response_format") val responseFormat: OpenAiResponseFormat,
)

@Serializable
internal data class OpenAiChoiceMessage(
    val content: String? = null,
)

@Serializable
internal data class OpenAiChoice(
    val message: OpenAiChoiceMessage? = null,
)

@Serializable
internal data class OpenAiResponse(
    val choices: List<OpenAiChoice> = emptyList(),
)

@Serializable
internal data class OpenAiErrorEnvelope(
    val error: OpenAiErrorBody? = null,
)

@Serializable
internal data class OpenAiErrorBody(
    val message: String? = null,
    val type: String? = null,
)
