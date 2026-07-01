package com.englishteacher.core.usecase

import com.englishteacher.core.catalog.TopicCatalog
import com.englishteacher.core.domain.model.ConversationSession
import com.englishteacher.core.domain.model.Topic
import com.englishteacher.core.domain.model.TopicCategory
import com.englishteacher.core.domain.port.SessionRepository

/** Resumes a previous conversation, by id or the most recent one. */
class ContinueSessionUseCase(
    private val repository: SessionRepository,
) {
    suspend fun byId(id: String): ConversationSession? = repository.get(id)

    suspend fun mostRecent(): ConversationSession? = repository.mostRecent()

    suspend fun history(): List<ConversationSession> = repository.all()
}

/** Lists available topics for the topic-picker screen. */
class ListTopicsUseCase(
    private val catalog: TopicCatalog = TopicCatalog,
) {
    operator fun invoke(): List<Topic> = catalog.all

    fun byCategory(category: TopicCategory): List<Topic> = catalog.byCategory(category)
}
