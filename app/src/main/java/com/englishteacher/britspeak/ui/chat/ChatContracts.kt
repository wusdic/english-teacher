package com.englishteacher.britspeak.ui.chat

import com.englishteacher.britspeak.ui.avatar.AvatarMood
import com.englishteacher.core.domain.model.ConversationSession
import com.englishteacher.core.domain.model.FeedbackLanguage
import com.englishteacher.core.domain.model.Speaker
import com.englishteacher.core.usecase.RepeatScore

/** High-level phase of the practice loop, driving both UI affordances and the avatar. */
enum class ChatPhase {
    IDLE,
    LISTENING,
    THINKING,
    SPEAKING,
    REPEATING,
}

/** Maps a [ChatPhase] to the tutor avatar's [AvatarMood]. Pure — unit-tested. */
fun avatarMoodFor(phase: ChatPhase): AvatarMood =
    when (phase) {
        ChatPhase.IDLE -> AvatarMood.IDLE
        ChatPhase.LISTENING, ChatPhase.REPEATING -> AvatarMood.LISTENING
        ChatPhase.THINKING -> AvatarMood.THINKING
        ChatPhase.SPEAKING -> AvatarMood.SPEAKING
    }

/** Immutable UI state for the practice screen. */
data class ChatUiState(
    val session: ConversationSession? = null,
    val topicTitle: String = "",
    val phase: ChatPhase = ChatPhase.IDLE,
    val partialTranscript: String = "",
    val feedbackLanguage: FeedbackLanguage = FeedbackLanguage.CHINESE,
    val pendingRepeat: String? = null,
    val lastRepeatScore: RepeatScore? = null,
    val hasApiKey: Boolean = false,
    val subtitlesEnabled: Boolean = true,
    val isBusy: Boolean = false,
    val error: String? = null,
) {
    val avatarMood: AvatarMood get() = avatarMoodFor(phase)
    val canSpeak: Boolean get() = hasApiKey && phase != ChatPhase.THINKING && phase != ChatPhase.SPEAKING

    /** Caption text for the optional bottom subtitle bar. */
    val subtitle: String
        get() =
            when (phase) {
                ChatPhase.SPEAKING ->
                    session?.messages?.lastOrNull { it.speaker == Speaker.TUTOR }?.text.orEmpty()
                ChatPhase.LISTENING, ChatPhase.REPEATING -> partialTranscript
                else -> ""
            }
}
