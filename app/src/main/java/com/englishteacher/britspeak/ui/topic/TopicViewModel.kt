package com.englishteacher.britspeak.ui.topic

import androidx.lifecycle.ViewModel
import com.englishteacher.core.domain.model.Topic
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
    ) : ViewModel() {
        val uiState: TopicUiState =
            TopicUiState(
                topicOfTheDay = dailyTopicSelector.topicForTimestamp(clock.nowMillis()),
                topics = listTopics(),
            )
    }
