package com.englishteacher.britspeak

import com.englishteacher.britspeak.speech.VoskHypothesisParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class VoskHypothesisParserTest {
    @Test
    fun `extracts partial text`() {
        assertEquals("hello there", VoskHypothesisParser.extract("""{"partial":"hello there"}""", "partial"))
    }

    @Test
    fun `extracts final text`() {
        assertEquals(
            "i would like the soup",
            VoskHypothesisParser.extract("""{"text":"i would like the soup"}""", "text"),
        )
    }

    @Test
    fun `missing key returns null`() {
        assertNull(VoskHypothesisParser.extract("""{"text":"hi"}""", "partial"))
    }

    @Test
    fun `blank value returns null`() {
        assertNull(VoskHypothesisParser.extract("""{"text":"   "}""", "text"))
    }

    @Test
    fun `null or malformed input returns null`() {
        assertNull(VoskHypothesisParser.extract(null, "text"))
        assertNull(VoskHypothesisParser.extract("not json", "text"))
        assertNull(VoskHypothesisParser.extract("", "text"))
    }
}
