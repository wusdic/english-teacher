package com.englishteacher.core.ai

import com.englishteacher.core.domain.model.ConversationSession
import com.englishteacher.core.domain.model.FeedbackLanguage
import com.englishteacher.core.domain.model.ProficiencyLevel
import com.englishteacher.core.domain.port.TutorEngineException
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/** End-to-end test of the real OkHttp + serialization path against an in-process server. */
class AnthropicTutorEngineTest {
    private lateinit var server: MockWebServer

    @BeforeTest
    fun setUp() {
        server = MockWebServer()
        server.start()
    }

    @AfterTest
    fun tearDown() {
        server.shutdown()
    }

    private fun engine(model: String = "claude-opus-4-8") =
        AnthropicTutorEngine(
            config =
                AnthropicConfig(
                    apiKey = "test-key",
                    model = model,
                    baseUrl = server.url("/").toString().trimEnd('/'),
                ),
        )

    private fun session() =
        ConversationSession(
            id = "s1",
            topicId = "restaurant",
            title = "At a Restaurant",
            createdAtMillis = 0,
            updatedAtMillis = 0,
            proficiency = ProficiencyLevel.INTERMEDIATE,
            feedbackLanguage = FeedbackLanguage.CHINESE,
        )

    private fun apiResponse(structuredJson: String): MockResponse =
        MockResponse()
            .setResponseCode(200)
            .setHeader("content-type", "application/json")
            .setBody(
                """
                {
                  "id": "msg_1",
                  "type": "message",
                  "role": "assistant",
                  "model": "claude-opus-4-8",
                  "stop_reason": "end_turn",
                  "content": [{"type": "text", "text": ${Json.encodeToString(String.serializer(), structuredJson)}}]
                }
                """.trimIndent(),
            )

    @Test
    fun `sends a correctly shaped request and parses the structured reply`() =
        runTest {
            val structured =
                """
                {"reply":"Lovely choice! Anything to drink?","hasErrors":true,
                 "corrections":[{"original":"I want soup","corrected":"I'd like the soup, please",
                 "type":"naturalness","explanationEn":"More polite.","explanationZh":"更礼貌。"}],
                 "repeatTarget":"I'd like the soup, please."}
                """.trimIndent()
            server.enqueue(apiResponse(structured))

            val turn = engine().respond(session(), "I want soup")

            // --- assert response parsed ---
            assertEquals("Lovely choice! Anything to drink?", turn.reply)
            assertTrue(turn.hasErrors)
            assertEquals("I'd like the soup, please.", turn.repeatTarget)

            // --- assert request shape ---
            val recorded = server.takeRequest()
            assertEquals("POST", recorded.method)
            assertEquals("/v1/messages", recorded.path)
            assertEquals("test-key", recorded.getHeader("x-api-key"))
            assertEquals("2023-06-01", recorded.getHeader("anthropic-version"))

            val body = Json.parseToJsonElement(recorded.body.readUtf8()).jsonObject
            assertEquals("claude-opus-4-8", body["model"]!!.jsonPrimitive.content)
            assertTrue(body.containsKey("system"))
            assertTrue(body.containsKey("messages"))
            // structured-output schema is present
            val schema =
                body["output_config"]!!.jsonObject["format"]!!.jsonObject["schema"]!!.jsonObject
            assertTrue(schema.containsKey("properties"))
            assertEquals("json_schema", body["output_config"]!!.jsonObject["format"]!!
                .jsonObject["type"]!!.jsonPrimitive.content)
        }

    @Test
    fun `non-2xx response raises a TutorEngineException with the api message`() =
        runTest {
            server.enqueue(
                MockResponse()
                    .setResponseCode(401)
                    .setBody("""{"type":"error","error":{"type":"authentication_error","message":"invalid x-api-key"}}"""),
            )

            val ex =
                assertFailsWith<TutorEngineException> {
                    engine().respond(session(), "hello")
                }
            assertTrue(ex.message!!.contains("invalid x-api-key"))
        }

    @Test
    fun `response with no text content raises`() =
        runTest {
            server.enqueue(
                MockResponse()
                    .setResponseCode(200)
                    .setBody("""{"content":[],"stop_reason":"end_turn"}"""),
            )
            assertFailsWith<TutorEngineException> { engine().respond(session(), "hi") }
        }

    @Test
    fun `opener works with no history`() =
        runTest {
            val structured =
                """{"reply":"Welcome in! Table for one?","hasErrors":false,"corrections":[],"repeatTarget":""}"""
            server.enqueue(apiResponse(structured))

            val turn = engine().opener(session())
            assertEquals("Welcome in! Table for one?", turn.reply)

            val body = Json.parseToJsonElement(server.takeRequest().body.readUtf8()).jsonObject
            // The API contract requires the first message to be from the user.
            val firstMessage = body["messages"]!!.jsonArray.first().jsonObject
            assertEquals("user", firstMessage["role"]!!.jsonPrimitive.content)
        }

    @Test
    fun `a custom scenario prompt overrides the catalog topic in the system prompt`() =
        runTest {
            server.enqueue(
                apiResponse(
                    """{"reply":"Sure!","hasErrors":false,"corrections":[],"repeatTarget":"Sure!"}""",
                ),
            )
            // topicId doesn't even need to exist in the catalog — the carried scenario wins.
            val custom =
                session().copy(
                    topicId = "custom",
                    customScenarioPrompt = "You run a spaceship bridge crew drill with the learner.",
                )

            engine().respond(custom, "Ready for launch")

            val body = Json.parseToJsonElement(server.takeRequest().body.readUtf8()).jsonObject
            val system = body["system"]!!.jsonPrimitive.content
            assertTrue(system.contains("spaceship bridge crew"))
        }
}
