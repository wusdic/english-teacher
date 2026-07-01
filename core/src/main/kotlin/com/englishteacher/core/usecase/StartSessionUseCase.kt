package com.englishteacher.core.usecase

import com.englishteacher.core.domain.model.ChatMessage
import com.englishteacher.core.domain.model.ConversationSession
import com.englishteacher.core.domain.model.Speaker
import com.englishteacher.core.domain.model.Topic
import com.englishteacher.core.domain.port.Clock
import com.englishteacher.core.domain.port.IdGenerator
import com.englishteacher.core.domain.port.LearnerPreferences
import com.englishteacher.core.domain.port.SessionRepository

/**
 * Starts a brand-new conversation on a [Topic]. The opening tutor line is taken from the
 * topic's curated openers (instant, offline, no API cost); the model takes over from the
 * learner's first reply onward.
 */
class StartSessionUseCase(
    private val clock: Clock,
    private val idGenerator: IdGenerator,
    private val repository: SessionRepository,
) {
    suspend operator fun invoke(
        topic: Topic,
        preferences: LearnerPreferences,
    ): ConversationSession {
        val now = clock.nowMillis()
        val openerIndex = Math.floorMod(now / 1000, topic.sampleOpeners.size.toLong()).toInt()
        val opener = topic.sampleOpeners[openerIndex]

        val firstMessage =
            ChatMessage(
                id = idGenerator.newId(),
                speaker = Speaker.TUTOR,
                text = opener,
                timestampMillis = now,
            )

        val session =
            ConversationSession(
                id = idGenerator.newId(),
                topicId = topic.id,
                title = topic.title,
                createdAtMillis = now,
                updatedAtMillis = now,
                proficiency = preferences.proficiency,
                feedbackLanguage = preferences.feedbackLanguage,
                messages = listOf(firstMessage),
            )

        repository.save(session)
        return session
    }
}
