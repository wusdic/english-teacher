package com.englishteacher.core.support

import com.englishteacher.core.domain.model.ConversationSession
import com.englishteacher.core.domain.model.TutorTurn
import com.englishteacher.core.domain.port.Clock
import com.englishteacher.core.domain.port.IdGenerator
import com.englishteacher.core.domain.port.SessionRepository
import com.englishteacher.core.domain.port.TutorEngine

/** Monotonic clock that advances by a fixed step on each read; deterministic for tests. */
class FakeClock(
    start: Long = 1_000L,
    private val step: Long = 1L,
) : Clock {
    private var current = start

    override fun nowMillis(): Long {
        val value = current
        current += step
        return value
    }
}

/** Sequential id generator: id-1, id-2, … */
class FakeIdGenerator : IdGenerator {
    private var counter = 0

    override fun newId(): String = "id-${++counter}"
}

/** In-memory [SessionRepository] for tests. */
class InMemorySessionRepository : SessionRepository {
    private val store = LinkedHashMap<String, ConversationSession>()

    override suspend fun save(session: ConversationSession) {
        store[session.id] = session
    }

    override suspend fun get(id: String): ConversationSession? = store[id]

    override suspend fun all(): List<ConversationSession> =
        store.values.sortedByDescending { it.updatedAtMillis }

    override suspend fun mostRecent(): ConversationSession? = all().firstOrNull()

    override suspend fun delete(id: String) {
        store.remove(id)
    }
}

/** [TutorEngine] that returns a canned turn and records the last utterance it saw. */
class FakeTutorEngine(
    private val turn: TutorTurn,
) : TutorEngine {
    var lastUtterance: String? = null
        private set
    var lastSession: ConversationSession? = null
        private set

    override suspend fun respond(
        session: ConversationSession,
        userUtterance: String,
    ): TutorTurn {
        lastSession = session
        lastUtterance = userUtterance
        return turn
    }
}
