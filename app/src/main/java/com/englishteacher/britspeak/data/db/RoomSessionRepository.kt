package com.englishteacher.britspeak.data.db

import com.englishteacher.core.domain.model.ConversationSession
import com.englishteacher.core.domain.port.SessionRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/** Room-backed implementation of the core [SessionRepository] port. */
@Singleton
class RoomSessionRepository
    @Inject
    constructor(
        private val dao: SessionDao,
    ) : SessionRepository {
        override suspend fun save(session: ConversationSession) {
            dao.upsert(SessionMappers.toEntity(session))
        }

        override suspend fun get(id: String): ConversationSession? =
            dao.get(id)?.let(SessionMappers::toDomain)

        override suspend fun all(): List<ConversationSession> =
            dao.all().map(SessionMappers::toDomain)

        override suspend fun mostRecent(): ConversationSession? =
            dao.mostRecent()?.let(SessionMappers::toDomain)

        override suspend fun delete(id: String) {
            dao.delete(id)
        }

        /** Reactive history stream for the History screen. */
        fun observeAll(): Flow<List<ConversationSession>> =
            dao.observeAll().map { list -> list.map(SessionMappers::toDomain) }
    }
