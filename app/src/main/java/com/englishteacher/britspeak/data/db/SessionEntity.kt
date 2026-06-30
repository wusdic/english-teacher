package com.englishteacher.britspeak.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * A persisted conversation. The transcript is stored as a JSON blob ([messagesJson]) to keep
 * the schema simple while preserving the rich nested correction data.
 */
@Entity(tableName = "sessions")
data class SessionEntity(
    @PrimaryKey val id: String,
    val topicId: String,
    val title: String,
    val createdAtMillis: Long,
    val updatedAtMillis: Long,
    val proficiency: String,
    val feedbackLanguage: String,
    val messagesJson: String,
)
