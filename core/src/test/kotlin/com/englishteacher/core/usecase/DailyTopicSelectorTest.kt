package com.englishteacher.core.usecase

import com.englishteacher.core.catalog.TopicCatalog
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class DailyTopicSelectorTest {
    private val selector = DailyTopicSelector()

    @Test
    fun `same day yields the same topic`() {
        assertEquals(selector.topicForDay(20_000), selector.topicForDay(20_000))
    }

    @Test
    fun `consecutive days rotate the topic`() {
        val a = selector.topicForDay(20_000)
        val b = selector.topicForDay(20_001)
        assertTrue(a != b, "adjacent days should differ within the rotation")
    }

    @Test
    fun `rotation wraps around the catalogue`() {
        // The rotation is every catalogue topic except free chat.
        val rotationSize = TopicCatalog.all.size - 1
        assertTrue(rotationSize > 0)
        // A full cycle returns to the same topic.
        assertEquals(
            selector.topicForDay(100),
            selector.topicForDay(100 + rotationSize.toLong()),
        )
    }

    @Test
    fun `daily topic never selects free chat`() {
        for (day in 0L..40L) {
            assertTrue(selector.topicForDay(day).id != "free_chat")
        }
    }

    @Test
    fun `timestamp maps to a stable day`() {
        // Start of a UTC day so a few hours later stays on the same day.
        val startOfDay = 19_675L * 24 * 60 * 60 * 1000
        val laterSameDay = startOfDay + 6 * 60 * 60 * 1000L
        assertEquals(
            selector.topicForTimestamp(startOfDay),
            selector.topicForTimestamp(laterSameDay),
        )
    }
}
