package com.englishteacher.core.catalog

import com.englishteacher.core.domain.model.TopicCategory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class TopicCatalogTest {
    @Test
    fun `all topic ids are unique`() {
        val ids = TopicCatalog.all.map { it.id }
        assertEquals(ids.size, ids.toSet().size, "topic ids must be unique")
    }

    @Test
    fun `free chat is present and resolvable`() {
        assertNotNull(TopicCatalog.byId("free_chat"))
        assertEquals(TopicCatalog.freeChat, TopicCatalog.byId("free_chat"))
    }

    @Test
    fun `unknown id resolves to null`() {
        assertNull(TopicCatalog.byId("does_not_exist"))
    }

    @Test
    fun `every topic has openers and a scenario`() {
        TopicCatalog.all.forEach { topic ->
            assertTrue(topic.sampleOpeners.isNotEmpty(), "${topic.id} needs openers")
            assertTrue(topic.scenarioPrompt.isNotBlank(), "${topic.id} needs a scenario")
        }
    }

    @Test
    fun `category filter returns matching topics`() {
        val travel = TopicCatalog.byCategory(TopicCategory.TRAVEL)
        assertTrue(travel.isNotEmpty())
        assertTrue(travel.all { it.category == TopicCategory.TRAVEL })
    }
}
