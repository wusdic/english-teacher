package com.englishteacher.britspeak

import com.englishteacher.britspeak.speech.PendingSpeechQueue
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PendingSpeechQueueTest {
    @Test
    fun `items submitted before ready are buffered and flushed in order`() {
        val q = PendingSpeechQueue<String>()
        assertEquals(PendingSpeechQueue.Decision.BUFFER, q.submit("filler"))
        assertEquals(PendingSpeechQueue.Decision.BUFFER, q.submit("sentence 1"))
        assertEquals(PendingSpeechQueue.Decision.BUFFER, q.submit("sentence 2"))

        // Regression: the whole first response must survive TTS init, in submission order.
        assertEquals(listOf("filler", "sentence 1", "sentence 2"), q.markReady())
    }

    @Test
    fun `items submitted after ready are spoken immediately`() {
        val q = PendingSpeechQueue<String>()
        assertTrue(q.markReady().isEmpty())
        assertEquals(PendingSpeechQueue.Decision.SPEAK, q.submit("hello"))
        assertTrue(q.isSettled())
    }

    @Test
    fun `items submitted after failure are dropped, and buffered ones are released once`() {
        val q = PendingSpeechQueue<String>()
        q.submit("buffered while initialising")

        // Init failed → the buffered item is returned so its caller's onDone can fire.
        assertEquals(listOf("buffered while initialising"), q.markFailed())
        // Anything submitted afterwards is dropped, not buffered forever.
        assertEquals(PendingSpeechQueue.Decision.DROP, q.submit("too late"))
        // markFailed is one-shot: it doesn't re-release.
        assertTrue(q.markFailed().isEmpty())
    }

    @Test
    fun `markReady is one-shot and wins over a later markFailed`() {
        val q = PendingSpeechQueue<String>()
        q.submit("a")
        assertEquals(listOf("a"), q.markReady())
        assertTrue(q.markReady().isEmpty())
        // A late failure signal after we're already ready must not release/duplicate anything.
        assertTrue(q.markFailed().isEmpty())
        assertEquals(PendingSpeechQueue.Decision.SPEAK, q.submit("b"))
    }

    @Test
    fun `drainBuffered removes pending items without settling the queue`() {
        val q = PendingSpeechQueue<String>()
        q.submit("x")
        q.submit("y")
        assertEquals(listOf("x", "y"), q.drainBuffered())
        assertFalse(q.isSettled())
        // Already drained, so markReady has nothing left to flush.
        assertTrue(q.markReady().isEmpty())
    }
}
