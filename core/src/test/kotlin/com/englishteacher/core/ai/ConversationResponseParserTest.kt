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
    fun `malformed json is rejected`() {
        assertFailsWith<TutorEngineException> { parser.parse("not json at all") }
    }
}
