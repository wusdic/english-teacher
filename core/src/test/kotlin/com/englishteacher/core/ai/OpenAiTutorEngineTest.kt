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

class OpenAiTutorEngineTest {
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

    private fun engine() =
        OpenAiTutorEngine(
            config =
                ProviderConfig(
                    provider = LlmProvider.OPENAI,
                    apiKey = "sk-test",
                    model = "gpt-4o",
                    baseUrl = server.url("/v1").toString().trimEnd('/'),
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

    private fun chatResponse(content: String): MockResponse =
        MockResponse()
            .setResponseCode(200)
            .setHeader("content-type", "application/json")
            .setBody(
                """
                {"id":"chatcmpl-1","object":"chat.completion",
                 "choices":[{"index":0,"message":{"role":"assistant","content":
                 ${Json.encodeToString(String.serializer(), content)}},"finish_reason":"stop"}]}
                """.trimIndent(),
            )

    @Test
    fun `sends chat completions request and parses json content`() =
        runTest {
            val structured =
                """{"reply":"Lovely! Anything to drink?","hasErrors":false,"corrections":[],"repeatTarget":"Anything to drink?"}"""
            server.enqueue(chatResponse(structured))

            val turn = engine().respond(session(), "I would like soup")
            assertEquals("Lovely! Anything to drink?", turn.reply)
            assertEquals("Anything to drink?", turn.repeatTarget)

            val recorded = server.takeRequest()
            assertEquals("POST", recorded.method)
            assertEquals("/v1/chat/completions", recorded.path)
            assertEquals("Bearer sk-test", recorded.getHeader("Authorization"))

            val body = Json.parseToJsonElement(recorded.body.readUtf8()).jsonObject
            assertEquals("gpt-4o", body["model"]!!.jsonPrimitive.content)
            assertEquals("json_object", body["response_format"]!!.jsonObject["type"]!!.jsonPrimitive.content)
            val messages = body["messages"]!!.jsonArray
            assertEquals("system", messages.first().jsonObject["role"]!!.jsonPrimitive.content)
            // system prompt carries the JSON-shape instruction
            assertTrue(
                messages.first().jsonObject["content"]!!.jsonPrimitive.content.contains("repeatTarget"),
            )
            assertEquals("user", messages.last().jsonObject["role"]!!.jsonPrimitive.content)
        }

    @Test
    fun `strips markdown code fences around json`() =
        runTest {
            val fenced =
                "```json\n{\"reply\":\"Hi there!\",\"hasErrors\":false,\"corrections\":[],\"repeatTarget\":\"Hi there!\"}\n```"
            server.enqueue(chatResponse(fenced))

            val turn = engine().respond(session(), "hello")
            assertEquals("Hi there!", turn.reply)
        }

    @Test
    fun `retries without response_format when the endpoint rejects json mode`() =
        runTest {
            // First attempt (json mode) fails as if the provider doesn't support response_format.
            server.enqueue(
                MockResponse()
                    .setResponseCode(400)
                    .setBody("""{"error":{"message":"response_format is not supported"}}"""),
            )
            // Retry (no json mode) succeeds.
            server.enqueue(
                chatResponse(
                    """{"reply":"All good!","hasErrors":false,"corrections":[],"repeatTarget":"All good!"}""",
                ),
            )

            val turn = engine().respond(session(), "hello")
            assertEquals("All good!", turn.reply)

            val first = Json.parseToJsonElement(server.takeRequest().body.readUtf8()).jsonObject
            assertTrue(first.containsKey("response_format"))
            val second = Json.parseToJsonElement(server.takeRequest().body.readUtf8()).jsonObject
            assertTrue(!second.containsKey("response_format"), "retry must omit response_format")
        }

    @Test
    fun `non-2xx raises with api message`() =
        runTest {
            // Both the json-mode attempt and the no-json-mode retry fail with the same auth error.
            repeat(2) {
                server.enqueue(
                    MockResponse()
                        .setResponseCode(401)
                        .setBody("""{"error":{"message":"Incorrect API key","type":"invalid_request_error"}}"""),
                )
            }
            val ex = assertFailsWith<TutorEngineException> { engine().respond(session(), "hi") }
            assertTrue(ex.message!!.contains("Incorrect API key"))
        }

    @Test
    fun `includes prior conversation turns in the request`() =
        runTest {
            server.enqueue(
                chatResponse(
                    """{"reply":"Go on!","hasErrors":false,"corrections":[],"repeatTarget":"Go on."}""",
                ),
            )
            val withHistory =
                session().copy(
                    messages =
                        listOf(
                            com.englishteacher.core.domain.model.ChatMessage(
                                "m1",
                                com.englishteacher.core.domain.model.Speaker.TUTOR,
                                "Welcome!",
                                1,
                            ),
                            com.englishteacher.core.domain.model.ChatMessage(
                                "m2",
                                com.englishteacher.core.domain.model.Speaker.USER,
                                "Hello",
                                2,
                            ),
                        ),
                )

            engine().respond(withHistory, "How are you?")

            val body = Json.parseToJsonElement(server.takeRequest().body.readUtf8()).jsonObject
            val messages = body["messages"]!!.jsonArray
            val roles = messages.map { it.jsonObject["role"]!!.jsonPrimitive.content }
            // system, assistant(Welcome), user(Hello), user(How are you?)
            assertEquals(listOf("system", "assistant", "user", "user"), roles)
            assertEquals("How are you?", messages.last().jsonObject["content"]!!.jsonPrimitive.content)
        }

    @Test
    fun `opener seeds a user message when history is empty`() =
        runTest {
            server.enqueue(
                chatResponse(
                    """{"reply":"Welcome in!","hasErrors":false,"corrections":[],"repeatTarget":""}""",
                ),
            )
            engine().opener(session())
            val body = Json.parseToJsonElement(server.takeRequest().body.readUtf8()).jsonObject
            val roles = body["messages"]!!.jsonArray.map { it.jsonObject["role"]!!.jsonPrimitive.content }
            assertTrue(roles.contains("user"), "must include a user message")
        }

    @Test
    fun `a custom scenario prompt overrides the catalog topic in the system prompt`() =
        runTest {
            server.enqueue(
                chatResponse(
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
            val system = body["messages"]!!.jsonArray.first().jsonObject["content"]!!.jsonPrimitive.content
            assertTrue(system.contains("spaceship bridge crew"))
        }
}
