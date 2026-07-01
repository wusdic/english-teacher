package com.englishteacher.core.usecase

import com.englishteacher.core.domain.model.ChatMessage
import com.englishteacher.core.domain.model.ConversationSession
import com.englishteacher.core.domain.model.Speaker
import com.englishteacher.core.domain.model.TutorStreamEvent
import com.englishteacher.core.domain.model.TutorTurn
import com.englishteacher.core.domain.port.Clock
import com.englishteacher.core.domain.port.IdGenerator
import com.englishteacher.core.domain.port.SessionRepository
import com.englishteacher.core.domain.port.TutorEngine
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

/** The session after a turn, plus the tutor turn that produced the latest reply. */
data class SendResult(
    val session: ConversationSession,
    val turn: TutorTurn,
)

/** Incremental events for [SendUtteranceUseCase.streamInvoke]. */
sealed interface SendStreamEvent {
    /** A newly-confirmed chunk of the tutor's spoken reply. */
    data class ReplyDelta(val text: String) : SendStreamEvent

    /** The turn is complete and has been persisted. */
    data class Done(val result: SendResult) : SendStreamEvent
}

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
        val withUser = appendUserMessage(session, utterance)
        val turn = engine.respond(withUser, utterance.trim())
        return finalize(withUser, turn)
    }

    /**
     * Same as [invoke], but streams the reply as the [TutorEngine] generates it — a caller can
     * start speaking/displaying each [SendStreamEvent.ReplyDelta] immediately instead of waiting
     * for the whole turn. The session is persisted once, when [SendStreamEvent.Done] fires.
     */
    fun streamInvoke(
        session: ConversationSession,
        utterance: String,
    ): Flow<SendStreamEvent> =
        flow {
            val withUser = appendUserMessage(session, utterance)
            engine.streamRespond(withUser, utterance.trim()).collect { event ->
                when (event) {
                    is TutorStreamEvent.ReplyDelta -> emit(SendStreamEvent.ReplyDelta(event.text))
                    is TutorStreamEvent.Done -> emit(SendStreamEvent.Done(finalize(withUser, event.turn)))
                }
            }
        }

    private fun appendUserMessage(
        session: ConversationSession,
        utterance: String,
    ): ConversationSession {
        require(utterance.isNotBlank()) { "Utterance must not be blank" }
        val userMessage =
            ChatMessage(
                id = idGenerator.newId(),
                speaker = Speaker.USER,
                text = utterance.trim(),
                timestampMillis = clock.nowMillis(),
            )
        return session.withMessage(userMessage)
    }

    private suspend fun finalize(
        withUser: ConversationSession,
        turn: TutorTurn,
    ): SendResult {
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
