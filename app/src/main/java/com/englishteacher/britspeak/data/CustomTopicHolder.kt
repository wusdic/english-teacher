package com.englishteacher.britspeak.data

import com.englishteacher.core.domain.model.Topic
import javax.inject.Inject
import javax.inject.Singleton

/**
 * One-shot hand-off for a user-authored custom scenario: the topic picker builds a [Topic] from
 * the learner's typed background/goal and stashes it here, then navigates to the practice screen
 * with the reserved [CUSTOM_TOPIC_ID]; [ChatViewModel][com.englishteacher.britspeak.ui.chat.ChatViewModel]
 * consumes it once when starting the session. Not used for persistence — once a session exists,
 * its scenario lives on [com.englishteacher.core.domain.model.ConversationSession.customScenarioPrompt].
 */
@Singleton
class CustomTopicHolder
    @Inject
    constructor() {
        private var pending: Topic? = null

        fun stash(topic: Topic) {
            pending = topic
        }

        /** Returns and clears the pending topic, if any. */
        fun consume(): Topic? = pending.also { pending = null }

        companion object {
            const val CUSTOM_TOPIC_ID = "custom"
        }
    }
