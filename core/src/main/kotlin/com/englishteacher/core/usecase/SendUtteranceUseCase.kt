package com.englishteacher.core.usecase

import com.englishteacher.core.domain.model.ChatMessage
import com.englishteacher.core.domain.model.ConversationSession
import com.englishteacher.core.domain.model.Speaker
import com.englishteacher.core.domain.model.TutorTurn
import com.englishteacher.core.domain.port.Clock
import com.englishteacher.core.domain.port.IdGenerator
import com.englishteacher.core.domain.port.SessionRepository
import com.englishteacher.core.domain.port.TutorEngine

/** The session after a turn, plus the tutor turn that produced the latest reply. */
data class SendResult(
    val session: ConversationSession,
    val turn: TutorTurn,
)

/**
 * Sends the learner's spoken utterance to the tutor and records both sides of the exchange.
 *
 * Flow: append the learner message → ask the [TutorEngine] for a reply → append the tutor
 * message (carrying corrections + repeat target) → persist.
 */
class SendUtteranceUseCase(
    private val engine: TutorEngine,
    private val clock: Clock,
    private val idGenerator: IdGenerator,
    private val repository: SessionRepository,
) {
    suspend operator fun invoke(
        session: ConversationSession,
        utterance: String,
    ): SendResult {
        require(utterance.isNotBlank()) { "Utterance must not be blank" }

        val userMessage =
            ChatMessage(
                id = idGenerator.newId(),
                speaker = Speaker.USER,
                text = utterance.trim(),
                timestampMillis = clock.nowMillis(),
            )
        val withUser = session.withMessage(userMessage)

        val turn = engine.respond(withUser, utterance.trim())

        val tutorMessage =
            ChatMessage(
                id = idGenerator.newId(),
                speaker = Speaker.TUTOR,
                text = turn.reply,
                timestampMillis = clock.nowMillis(),
                corrections = turn.corrections,
                repeatTarget = turn.repeatTarget,
            )
        val updated = withUser.withMessage(tutorMessage)

        repository.save(updated)
        return SendResult(session = updated, turn = turn)
    }
}
