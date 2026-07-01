package com.englishteacher.core.ai

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class StreamingReplyAccumulatorTest {
    @Test
    fun `plain reply with no markers is emitted as-is`() {
        val acc = StreamingReplyAccumulator()
        val out = StringBuilder()
        for (chunk in listOf("Hello ", "there", "!")) out.append(acc.feed(chunk))

        assertEquals("Hello there!", out.toString())
        val (reply, json) = acc.finishReplyAndJson()
        assertEquals("Hello there!", reply)
        assertEquals("", json)
    }

    @Test
    fun `delimiter splits reply from the json tail`() {
        val acc = StreamingReplyAccumulator()
        val out = StringBuilder()
        for (chunk in listOf("Lovely, tell me more!", "###JSON###", """{"hasErrors":false}""")) {
            out.append(acc.feed(chunk))
        }

        assertEquals("Lovely, tell me more!", out.toString())
        val (reply, json) = acc.finishReplyAndJson()
        assertEquals("Lovely, tell me more!", reply)
        assertEquals("""{"hasErrors":false}""", json)
    }

    @Test
    fun `delimiter split across chunk boundaries is not leaked into the reply`() {
        val acc = StreamingReplyAccumulator()
        val out = StringBuilder()
        // The delimiter arrives split awkwardly across three chunks.
        for (chunk in listOf("Great job", "! ###JS", "ON###", """{"hasErrors":true}""")) {
            out.append(acc.feed(chunk))
        }

        assertEquals("Great job! ", out.toString())
        val (reply, json) = acc.finishReplyAndJson()
        assertEquals("Great job!", reply)
        assertEquals("""{"hasErrors":true}""", json)
        assertTrue(!reply.contains("JSON"), "delimiter must never leak into the spoken reply")
    }

    @Test
    fun `a think block before the reply is discarded entirely`() {
        val acc = StreamingReplyAccumulator()
        val out = StringBuilder()
        for (chunk in listOf(
            "<think>",
            "the learner made a tense error, I should mention it",
            "</think>",
            "Nice one! ",
            "Tell me more.",
        )) {
            out.append(acc.feed(chunk))
        }

        assertEquals("Nice one! Tell me more.", out.toString())
        assertTrue(!out.contains("tense error"), "reasoning must never be spoken")
    }

    @Test
    fun `a think tag split across chunk boundaries is still detected and discarded`() {
        val acc = StreamingReplyAccumulator()
        val out = StringBuilder()
        for (chunk in listOf("<thi", "nking>", "hmm, let me consider", "</thi", "nking>", "Sure!")) {
            out.append(acc.feed(chunk))
        }

        assertEquals("Sure!", out.toString())
    }

    @Test
    fun `no delimiter at all leaves an empty json tail`() {
        val acc = StreamingReplyAccumulator()
        acc.feed("Just chatting, no structured output today.")
        val (reply, json) = acc.finishReplyAndJson()
        assertEquals("Just chatting, no structured output today.", reply)
        assertEquals("", json)
    }

    @Test
    fun `trailing partial marker is flushed as plain text if the stream ends early`() {
        val acc = StreamingReplyAccumulator()
        val out = StringBuilder()
        out.append(acc.feed("All done for now <thi"))
        // Stream ends abruptly (e.g. truncation) before the tag could ever complete.
        val (reply, _) = acc.finishReplyAndJson()
        assertEquals("All done for now <thi", reply)
        assertEquals("All done for now ", out.toString())
    }
}
