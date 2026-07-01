package com.englishteacher.core.domain.model

/**
 * A persisted conversation the learner can resume. Holds the full transcript plus the
 * settings the session was started with.
 */
data class ConversationSession(
    val id: String,
    val topicId: String,
    val title: String,
    val createdAtMillis: Long,
    val updatedAtMillis: Long,
    val proficiency: ProficiencyLevel,
    val feedbackLanguage: FeedbackLanguage,
    val messages: List<ChatMessage> = emptyList(),
    /**
     * The scenario/background/goal this session was started with, verbatim. Carried on the
     * session (rather than looked up again from the static catalog every turn) so a
     * user-authored custom scenario survives resuming the conversation later.
     */
    val customScenarioPrompt: String? = null,
) {
    /** Returns a copy with [message] appended and [updatedAtMillis] advanced. */
    fun withMessage(message: ChatMessage): ConversationSession =
        copy(
            messages = messages + message,
            updatedAtMillis = maxOf(updatedAtMillis, message.timestampMillis),
        )

    /** Conversation history excluding correction metadata, oldest first. */
    val transcript: List<ChatMessage> get() = messages
}
