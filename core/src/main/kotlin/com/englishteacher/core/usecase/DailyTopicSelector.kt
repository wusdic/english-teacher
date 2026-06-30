package com.englishteacher.core.usecase

import com.englishteacher.core.catalog.TopicCatalog
import com.englishteacher.core.domain.model.Topic

/**
 * Picks a "topic of the day" deterministically, so the same day always yields the same topic
 * (matching 「咕噜口语」's daily-theme behaviour) while rotating across the catalogue over time.
 */
class DailyTopicSelector(
    private val catalog: TopicCatalog = TopicCatalog,
) {
    /** Topics eligible for the daily rotation (excludes open-ended free chat). */
    private val rotation: List<Topic> = catalog.all.filter { it.id != catalog.freeChat.id }

    /** The topic for the given epoch-day number (days since 1970-01-01, local). */
    fun topicForDay(epochDay: Long): Topic {
        require(rotation.isNotEmpty()) { "Topic rotation must not be empty" }
        val index = Math.floorMod(epochDay, rotation.size.toLong()).toInt()
        return rotation[index]
    }

    /** Convenience for the day a given epoch-millis timestamp falls on at the given offset. */
    fun topicForTimestamp(
        epochMillis: Long,
        offsetSeconds: Int = 0,
    ): Topic {
        val localMillis = epochMillis + offsetSeconds * 1000L
        val epochDay = Math.floorDiv(localMillis, MILLIS_PER_DAY)
        return topicForDay(epochDay)
    }

    companion object {
        private const val MILLIS_PER_DAY = 24L * 60 * 60 * 1000
    }
}
