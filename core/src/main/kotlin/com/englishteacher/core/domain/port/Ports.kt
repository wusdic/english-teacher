package com.englishteacher.core.domain.port

import com.englishteacher.core.domain.model.ConversationSession
import com.englishteacher.core.domain.model.ProficiencyLevel
import com.englishteacher.core.domain.model.TutorStreamEvent
import com.englishteacher.core.domain.model.TutorTurn
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

/** Supplies the current wall-clock time; injected so logic stays deterministic in tests. */
fun interface Clock {
    fun nowMillis(): Long
}

/** Generates unique identifiers for sessions and messages. */
fun interface IdGenerator {
    fun newId(): String
}

/**
 * Talks to the conversational AI. Given the running [session] and the learner's latest
 * [userUtterance], returns the tutor's next [TutorTurn].
 *
 * Implementations may throw [TutorEngineException] on transport/parse failures.
 */
interface TutorEngine {
    suspend fun respond(
        session: ConversationSession,
        userUtterance: String,
    ): TutorTurn

    /**
     * Produces the tutor's opening line for a brand-new session (no learner utterance yet).
     * Default implementation falls back to a neutral greeting and may be overridden for a
     * model-generated opener.
     */
    suspend fun opener(session: ConversationSession): TutorTurn =
        TutorTurn(reply = "Hello! Shall we begin?", corrections = emptyList(), repeatTarget = null)

    /**
     * Streams the tutor's next turn as it is generated, so a caller can start speaking the
     * reply before the whole turn (corrections, repeat target, ...) has arrived. Emits zero or
     * more [TutorStreamEvent.ReplyDelta]s followed by exactly one [TutorStreamEvent.Done].
     *
     * The default wraps the non-streaming [respond] as a single delta — engines that don't
     * implement real token streaming still work correctly through a streaming caller, just
     * without the latency benefit.
     */
    fun streamRespond(
        session: ConversationSession,
        userUtterance: String,
    ): Flow<TutorStreamEvent> =
        flow {
            val turn = respond(session, userUtterance)
            if (turn.reply.isNotBlank()) {
                emit(TutorStreamEvent.ReplyDelta(turn.reply))
            }
            emit(TutorStreamEvent.Done(turn))
        }
}

/** Raised by a [TutorEngine] when it cannot produce a usable response. */
class TutorEngineException(message: String, cause: Throwable? = null) :
    RuntimeException(message, cause)

/** Persists and retrieves [ConversationSession]s. */
interface SessionRepository {
    suspend fun save(session: ConversationSession)

    suspend fun get(id: String): ConversationSession?

    /** All sessions, most-recently-updated first. */
    suspend fun all(): List<ConversationSession>

    /** The most-recently-updated session, or null if none exist. */
    suspend fun mostRecent(): ConversationSession?

    suspend fun delete(id: String)
}

/** User-tunable defaults applied to new sessions. */
data class LearnerPreferences(
    val proficiency: ProficiencyLevel = ProficiencyLevel.INTERMEDIATE,
    val feedbackLanguage: com.englishteacher.core.domain.model.FeedbackLanguage =
        com.englishteacher.core.domain.model.FeedbackLanguage.CHINESE,
)
