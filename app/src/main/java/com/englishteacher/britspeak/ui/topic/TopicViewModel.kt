package com.englishteacher.britspeak.ui.topic

import androidx.lifecycle.ViewModel
import com.englishteacher.britspeak.data.CustomTopicHolder
import com.englishteacher.core.domain.model.Topic
import com.englishteacher.core.domain.model.TopicCategory
import com.englishteacher.core.domain.port.Clock
import com.englishteacher.core.usecase.DailyTopicSelector
import com.englishteacher.core.usecase.ListTopicsUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

data class TopicUiState(
    val topicOfTheDay: Topic,
    val topics: List<Topic>,
)

@HiltViewModel
class TopicViewModel
    @Inject
    constructor(
        listTopics: ListTopicsUseCase,
        dailyTopicSelector: DailyTopicSelector,
        clock: Clock,
        private val customTopicHolder: CustomTopicHolder,
    ) : ViewModel() {
        val uiState: TopicUiState =
            TopicUiState(
                topicOfTheDay = dailyTopicSelector.topicForTimestamp(clock.nowMillis()),
                topics = listTopics(),
            )

        /**
         * Builds a one-off custom scenario from the learner's own [background]/[goal] and stashes
         * it for [com.englishteacher.britspeak.ui.chat.ChatViewModel] to pick up. Returns the
         * reserved topic id to navigate to.
         */
        fun startCustomTopic(
            title: String,
            background: String,
            goal: String,
        ): String {
            val scenario =
                buildString {
                    append("SETTING: ${background.trim()}")
                    if (goal.isNotBlank()) {
                        append("\nYOUR GOAL FOR THIS CONVERSATION: ${goal.trim()}")
                    }
                    append(
                        "\nStay fully in character for this setting and steer the conversation " +
                            "toward the stated goal, the same way you would for any other role-play.",
                    )
                }
            val topic =
                Topic(
                    // Suffixed so each new custom scenario gets a distinct nav route/topicId —
                    // otherwise navigating to the same "practice?topicId=custom" route twice in a
                    // row wouldn't trigger a fresh session for the second scenario.
                    id = "${CustomTopicHolder.CUSTOM_TOPIC_ID}_${System.currentTimeMillis()}",
                    title = title.trim().ifBlank { "Custom Scenario" },
                    description = background.trim().take(120),
                    category = TopicCategory.EVERYDAY,
                    scenarioPrompt = scenario,
                    sampleOpeners = listOf("Hello! Let's get started — I'm ready when you are."),
                )
            customTopicHolder.stash(topic)
            return topic.id
        }
    }
