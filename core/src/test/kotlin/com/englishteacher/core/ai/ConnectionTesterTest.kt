package com.englishteacher.core.ai

import kotlinx.coroutines.test.runTest
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ConnectionTesterTest {
    private lateinit var server: MockWebServer
    private val tester = ConnectionTester()

    @BeforeTest
    fun setUp() {
        server = MockWebServer()
        server.start()
    }

    @AfterTest
    fun tearDown() {
        server.shutdown()
    }

    private fun config(provider: LlmProvider) =
        ProviderConfig(
            provider = provider,
            apiKey = "k",
            model = "m",
            baseUrl = server.url("/").toString().trimEnd('/'),
        )

    @Test
    fun `anthropic success returns the sample reply and hits messages endpoint`() =
        runTest {
            server.enqueue(
                MockResponse()
                    .setResponseCode(200)
                    .setBody("""{"content":[{"type":"text","text":"OK"}],"stop_reason":"end_turn"}"""),
            )
            val result = tester.test(config(LlmProvider.ANTHROPIC))
            assertTrue(result is ConnectionTestResult.Success)
            assertEquals("OK", (result as ConnectionTestResult.Success).sample)
            assertEquals("/v1/messages", server.takeRequest().path)
        }

    @Test
    fun `openai success hits chat completions endpoint`() =
        runTest {
            server.enqueue(
                MockResponse()
                    .setResponseCode(200)
                    .setBody("""{"choices":[{"message":{"role":"assistant","content":"OK"}}]}"""),
            )
            val result = tester.test(config(LlmProvider.OPENAI))
            assertTrue(result is ConnectionTestResult.Success)
            assertEquals("/chat/completions", server.takeRequest().path)
        }

    @Test
    fun `http error surfaces the api message`() =
        runTest {
            server.enqueue(
                MockResponse()
                    .setResponseCode(401)
                    .setBody("""{"error":{"message":"invalid x-api-key","type":"authentication_error"}}"""),
            )
            val result = tester.test(config(LlmProvider.ANTHROPIC))
            assertTrue(result is ConnectionTestResult.Failure)
            assertTrue((result as ConnectionTestResult.Failure).message.contains("invalid x-api-key"))
        }

    @Test
    fun `blank key fails fast without a request`() =
        runTest {
            val result = tester.test(config(LlmProvider.OPENAI).copy(apiKey = ""))
            assertTrue(result is ConnectionTestResult.Failure)
        }
}
