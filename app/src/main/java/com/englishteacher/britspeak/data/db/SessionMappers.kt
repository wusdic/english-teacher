package com.englishteacher.britspeak.data.db

import com.englishteacher.core.domain.model.ChatMessage
import com.englishteacher.core.domain.model.ConversationSession
import com.englishteacher.core.domain.model.Correction
import com.englishteacher.core.domain.model.CorrectionType
import com.englishteacher.core.domain.model.FeedbackLanguage
import com.englishteacher.core.domain.model.ProficiencyLevel
import com.englishteacher.core.domain.model.Speaker
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json

/**
 * Converts between the core [ConversationSession] domain model and the persisted
 * [SessionEntity]. The message transcript is (de)serialised via small mirror DTOs so the core
 * model can stay free of serialization annotations.
 */
object SessionMappers {
    private val json = Json { ignoreUnknownKeys = true }
    private val messageListSerializer = ListSerializer(PersistedMessage.serializer())

    @Serializable
    private data class PersistedCorrection(
        val original: String,
        val corrected: String,
        val type: String,
        val explanationEn: String,
        val explanationZh: String,
    )

    @Serializable
    private data class PersistedMessage(
        val id: String,
        val speaker: String,
        val text: String,
        val timestampMillis: Long,
        val corrections: List<PersistedCorrection> = emptyList(),
        val repeatTarget: String? = null,
    )

    fun toEntity(session: ConversationSession): SessionEntity {
        val messages =
            session.messages.map { m ->
                PersistedMessage(
                    id = m.id,
                    speaker = m.speaker.name,
                    text = m.text,
                    timestampMillis = m.timestampMillis,
                    corrections =
                        m.corrections.map { c ->
                            PersistedCorrection(
                                original = c.original,
                                corrected = c.corrected,
                                type = c.type.name,
                                explanationEn = c.explanationEn,
                                explanationZh = c.explanationZh,
                            )
                        },
                    repeatTarget = m.repeatTarget,
                )
            }
        return SessionEntity(
            id = session.id,
            topicId = session.topicId,
            title = session.title,
            createdAtMillis = session.createdAtMillis,
            updatedAtMillis = session.updatedAtMillis,
            proficiency = session.proficiency.name,
            feedbackLanguage = session.feedbackLanguage.name,
            messagesJson = json.encodeToString(messageListSerializer, messages),
            customScenarioPrompt = session.customScenarioPrompt,
        )
    }

    fun toDomain(entity: SessionEntity): ConversationSession {
        val messages =
            json.decodeFromString(messageListSerializer, entity.messagesJson).map { m ->
                ChatMessage(
                    id = m.id,
                    speaker = Speaker.valueOf(m.speaker),
                    text = m.text,
                    timestampMillis = m.timestampMillis,
                    corrections =
                        m.corrections.map { c ->
                            Correction(
                                original = c.original,
                                corrected = c.corrected,
                                type = CorrectionType.fromWire(c.type),
                                explanationEn = c.explanationEn,
                                explanationZh = c.explanationZh,
                            )
                        },
                    repeatTarget = m.repeatTarget,
                )
            }
        return ConversationSession(
            id = entity.id,
            topicId = entity.topicId,
            title = entity.title,
            createdAtMillis = entity.createdAtMillis,
            updatedAtMillis = entity.updatedAtMillis,
            proficiency = ProficiencyLevel.valueOf(entity.proficiency),
            feedbackLanguage = FeedbackLanguage.valueOf(entity.feedbackLanguage),
            messages = messages,
            customScenarioPrompt = entity.customScenarioPrompt,
        )
    }
}
