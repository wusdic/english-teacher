package com.englishteacher.core.domain.model

/** Broad grouping for topics, used for filtering and presentation. */
enum class TopicCategory {
    EVERYDAY,
    TRAVEL,
    WORK,
    SOCIAL,
    EXAM,
}

/**
 * A conversation theme or role-play scenario the learner can pick.
 *
 * [scenarioPrompt] is injected into the tutor system prompt to set the scene (e.g. "You are
 * a waiter in a London restaurant"). [sampleOpeners] seed the very first tutor line.
 */
data class Topic(
    val id: String,
    val title: String,
    val description: String,
    val category: TopicCategory,
    val scenarioPrompt: String,
    val sampleOpeners: List<String>,
) {
    init {
        require(id.isNotBlank()) { "Topic id must not be blank" }
        require(sampleOpeners.isNotEmpty()) { "Topic must have at least one opener" }
    }
}
