package com.englishteacher.core.ai

import com.englishteacher.core.domain.model.CorrectionType
import com.englishteacher.core.domain.port.TutorEngineException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ConversationResponseParserTest {
    private val parser = ConversationResponseParser()

    @Test
    fun `parses a full valid payload`() {
        val json =
            """
            {
              "reply": "Lovely! What did you have?",
              "hasErrors": true,
              "corrections": [
                {
                  "original": "I eat breakfast at 8 yesterday",
                  "corrected": "I had breakfast at 8 yesterday",
                  "type": "grammar",
                  "explanationEn": "Use the past simple 'had'.",
                  "explanationZh": "用一般过去时 'had'。"
                }
              ],
              "repeatTarget": "I had breakfast at 8 yesterday."
            }
            """.trimIndent()

        val turn = parser.parse(json)

        assertEquals("Lovely! What did you have?", turn.reply)
        assertTrue(turn.hasErrors)
        assertEquals(1, turn.corrections.size)
        assertEquals(CorrectionType.GRAMMAR, turn.corrections[0].type)
        assertEquals("I had breakfast at 8 yesterday", turn.corrections[0].corrected)
        assertEquals("I had breakfast at 8 yesterday.", turn.repeatTarget)
    }

    @Test
    fun `no corrections yields empty list and no errors`() {
        val json =
            """
            {"reply":"Great, carry on!","hasErrors":false,"corrections":[],"repeatTarget":"Carry on."}
            """.trimIndent()

        val turn = parser.parse(json)

        assertTrue(turn.corrections.isEmpty())
        assertEquals(false, turn.hasErrors)
        assertEquals("Carry on.", turn.repeatTarget)
    }

    @Test
    fun `blank repeat target becomes null`() {
        val json =
            """{"reply":"Hi!","hasErrors":false,"corrections":[],"repeatTarget":"   "}"""
        assertNull(parser.parse(json).repeatTarget)
    }

    @Test
    fun `unknown correction type falls back to naturalness`() {
        val json =
            """
            {"reply":"Ok","hasErrors":true,"corrections":[
              {"original":"a","corrected":"b","type":"slang","explanationEn":"x","explanationZh":"y"}
            ],"repeatTarget":"b"}
            """.trimIndent()

        assertEquals(CorrectionType.NATURALNESS, parser.parse(json).corrections[0].type)
    }

    @Test
    fun `corrections with blank corrected are dropped`() {
        val json =
            """
            {"reply":"Ok","hasErrors":true,"corrections":[
              {"original":"a","corrected":"","type":"grammar","explanationEn":"x","explanationZh":"y"}
            ],"repeatTarget":"b"}
            """.trimIndent()

        assertTrue(parser.parse(json).corrections.isEmpty())
    }

    @Test
    fun `unknown extra fields are ignored`() {
        val json =
            """{"reply":"Hi","hasErrors":false,"corrections":[],"repeatTarget":"Hi","mood":"happy"}"""
        assertEquals("Hi", parser.parse(json).reply)
    }

    @Test
    fun `blank reply is rejected`() {
        val json = """{"reply":"   ","hasErrors":false,"corrections":[],"repeatTarget":"x"}"""
        assertFailsWith<TutorEngineException> { parser.parse(json) }
    }

    @Test
    fun `json wrapped in prose is still parsed`() {
        // Some models (e.g. MiniMax) prepend/append chatter despite the "JSON only" instruction.
        val text =
            """
            Sure, here is my response:
            {"reply":"Nice to meet you!","hasErrors":false,"corrections":[],"repeatTarget":"Nice to meet you."}
            Hope that helps!
            """.trimIndent()

        val turn = parser.parse(text)
        assertEquals("Nice to meet you!", turn.reply)
        assertEquals("Nice to meet you.", turn.repeatTarget)
    }

    @Test
    fun `plain prose with no json is spoken as the reply`() {
        // Graceful degradation: rather than failing the turn, speak whatever the model said.
        val turn = parser.parse("Hello there, how are you today?")
        assertEquals("Hello there, how are you today?", turn.reply)
        assertTrue(turn.corrections.isEmpty())
        assertNull(turn.repeatTarget)
    }

    @Test
    fun `empty content is rejected`() {
        assertFailsWith<TutorEngineException> { parser.parse("   ") }
    }

    @Test
    fun `think tags wrapping the whole payload are stripped`() {
        // Reasoning models (DeepSeek-R1, QwQ, MiniMax reasoning mode, ...) may prefix the JSON
        // with a hidden chain-of-thought. The learner must never see it.
        val text =
            """
            <think>
            The learner said "I go there yesterday", that's a tense error, I should correct it...
            </think>
            {"reply":"Lovely, tell me more!","hasErrors":true,"corrections":[],"repeatTarget":"I went there yesterday."}
            """.trimIndent()

        val turn = parser.parse(text)
        assertEquals("Lovely, tell me more!", turn.reply)
        assertEquals("I went there yesterday.", turn.repeatTarget)
    }

    @Test
    fun `think tags inside the reply field are stripped`() {
        val json =
            """{"reply":"<thinking>hmm let me consider</thinking>Great job!","hasErrors":false,"corrections":[],"repeatTarget":"Great job."}"""
        assertEquals("Great job!", parser.parse(json).reply)
    }

    @Test
    fun `an unclosed think tag with no real answer is rejected`() {
        // A truncated reasoning response (ran out of tokens mid-thought) has no usable answer at
        // all — failing loudly here is correct so the caller can retry rather than speak nothing.
        val text = "<think>Let me think about how to respond to this learner..."
        assertFailsWith<TutorEngineException> { parser.parse(text) }
    }

    @Test
    fun `finalizeStreamedTurn combines the streamed reply with the json tail`() {
        val turn =
            parser.finalizeStreamedTurn(
                streamedReply = "Lovely, tell me more!",
                jsonTail =
                    """
                    {"hasErrors":true,"corrections":[
                      {"original":"I go there yesterday","corrected":"I went there yesterday",
                       "type":"grammar","explanationEn":"Past tense.","explanationZh":"过去式。"}
                    ],"repeatTarget":"I went there yesterday."}
                    """.trimIndent(),
            )

        assertEquals("Lovely, tell me more!", turn.reply)
        assertTrue(turn.hasErrors)
        assertEquals("I went there yesterday", turn.corrections[0].corrected)
        assertEquals("I went there yesterday.", turn.repeatTarget)
    }

    @Test
    fun `finalizeStreamedTurn degrades gracefully when the json tail is missing or malformed`() {
        val turn = parser.finalizeStreamedTurn(streamedReply = "All good, carry on!", jsonTail = "")
        assertEquals("All good, carry on!", turn.reply)
        assertTrue(turn.corrections.isEmpty())
        assertNull(turn.repeatTarget)

        val turn2 = parser.finalizeStreamedTurn(streamedReply = "Great!", jsonTail = "not json at all")
        assertEquals("Great!", turn2.reply)
        assertTrue(turn2.corrections.isEmpty())
    }

    @Test
    fun `finalizeStreamedTurn rejects a genuinely empty streamed reply`() {
        assertFailsWith<TutorEngineException> {
            parser.finalizeStreamedTurn(streamedReply = "   ", jsonTail = """{"hasErrors":false}""")
        }
    }
}
