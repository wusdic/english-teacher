package com.englishteacher.britspeak.ui.chat

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.englishteacher.britspeak.data.CustomTopicHolder
import com.englishteacher.britspeak.data.prefs.ApiKeyStore
import com.englishteacher.britspeak.data.prefs.SettingsStore
import com.englishteacher.britspeak.speech.FillerPhrases
import com.englishteacher.britspeak.speech.SpeechToText
import com.englishteacher.britspeak.speech.SttCallback
import com.englishteacher.britspeak.speech.TutorVoice
import com.englishteacher.core.catalog.TopicCatalog
import com.englishteacher.core.domain.model.ConversationSession
import com.englishteacher.core.domain.model.FeedbackLanguage
import com.englishteacher.core.domain.model.Speaker
import com.englishteacher.core.domain.port.Clock
import com.englishteacher.core.domain.port.LearnerPreferences
import com.englishteacher.core.usecase.ContinueSessionUseCase
import com.englishteacher.core.usecase.DailyTopicSelector
import com.englishteacher.core.usecase.RepeatScorer
import com.englishteacher.core.usecase.SendStreamEvent
import com.englishteacher.core.usecase.SendUtteranceUseCase
import com.englishteacher.core.usecase.SentenceChunker
import com.englishteacher.core.usecase.StartSessionUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Drives the practice screen: orchestrates speech-to-text, the tutor use-cases, and
 * text-to-speech, and exposes a single [ChatUiState].
 */
@HiltViewModel
class ChatViewModel
    @Inject
    constructor(
        private val startSession: StartSessionUseCase,
        private val sendUtterance: SendUtteranceUseCase,
        private val continueSession: ContinueSessionUseCase,
        private val repeatScorer: RepeatScorer,
        private val stt: SpeechToText,
        private val voice: TutorVoice,
        private val settingsStore: SettingsStore,
        private val apiKeyStore: ApiKeyStore,
        private val catalog: TopicCatalog,
        private val dailyTopicSelector: DailyTopicSelector,
        private val clock: Clock,
        private val customTopicHolder: CustomTopicHolder,
    ) : ViewModel() {
        private val _state = MutableStateFlow(ChatUiState())
        val state: StateFlow<ChatUiState> = _state.asStateFlow()

        private var listeningForRepeat = false

        init {
            viewModelScope.launch {
                val prefs = settingsStore.preferences.first()
                val subtitles = settingsStore.subtitlesEnabled.first()
                _state.update {
                    it.copy(
                        feedbackLanguage = prefs.feedbackLanguage,
                        hasApiKey = apiKeyStore.hasKey,
                        subtitlesEnabled = subtitles,
                    )
                }
            }
            // Reflect first-run offline-model loading so the UI can show a "loading" hint.
            viewModelScope.launch {
                if (!stt.isAvailable && stt.isLoading) {
                    _state.update { it.copy(sttLoading = true) }
                    var tries = 0
                    while (!stt.isAvailable && stt.isLoading && tries < 90) {
                        delay(1000)
                        tries++
                    }
                    _state.update { it.copy(sttLoading = false) }
                }
            }
        }

        /** Refreshes flags that may have changed while away (e.g. API key added in Settings). */
        fun refresh() {
            viewModelScope.launch {
                val prefs = settingsStore.preferences.first()
                val subtitles = settingsStore.subtitlesEnabled.first()
                _state.update {
                    it.copy(
                        hasApiKey = apiKeyStore.hasKey,
                        feedbackLanguage = prefs.feedbackLanguage,
                        subtitlesEnabled = subtitles,
                    )
                }
            }
        }

        fun toggleSubtitles() {
            val next = !_state.value.subtitlesEnabled
            _state.update { it.copy(subtitlesEnabled = next) }
            viewModelScope.launch { settingsStore.setSubtitlesEnabled(next) }
        }

        /** Resume the most recent conversation, or start today's topic if there is none. */
        fun resumeOrStartDaily() {
            val daily = dailyTopicSelector.topicForTimestamp(clock.nowMillis())
            resumeOrStart(daily.id)
        }

        /** Resume the most recent conversation, or start one on [fallbackTopicId] if none exists. */
        fun resumeOrStart(fallbackTopicId: String) {
            viewModelScope.launch {
                val existing = continueSession.mostRecent()
                if (existing != null) {
                    bind(existing)
                } else {
                    startOnTopic(fallbackTopicId)
                }
            }
        }

        /** Start a fresh conversation on a chosen topic. */
        fun startOnTopic(topicId: String) {
            viewModelScope.launch {
                // A user-authored custom scenario is handed off in-memory by TopicViewModel
                // rather than looked up in the static catalog.
                val topic =
                    if (topicId.startsWith(CustomTopicHolder.CUSTOM_TOPIC_ID)) {
                        customTopicHolder.consume()
                    } else {
                        null
                    } ?: catalog.byId(topicId) ?: catalog.freeChat
                val prefs = currentPreferences()
                val session = startSession(topic, prefs)
                bind(session)
                // Speak the opener line.
                session.messages.lastOrNull()?.let { speak(it.text, andThen = ChatPhase.IDLE) }
            }
        }

        fun openSession(sessionId: String) {
            viewModelScope.launch {
                continueSession.byId(sessionId)?.let(::bind)
            }
        }

        /** Begin capturing the learner's speech for a normal turn. */
        fun startListening() {
            if (!_state.value.canSpeak) return
            listeningForRepeat = false
            beginRecognition(ChatPhase.LISTENING)
        }

        /** Begin capturing the learner repeating the [ChatUiState.pendingRepeat] target. */
        fun startRepeat() {
            if (_state.value.pendingRepeat == null) return
            listeningForRepeat = true
            beginRecognition(ChatPhase.REPEATING)
        }

        fun stopListening() {
            stt.stopListening()
        }

        /** Replay a tutor message via TTS. */
        fun replay(text: String) {
            speak(text, andThen = ChatPhase.IDLE)
        }

        fun toggleFeedbackLanguage() {
            val next =
                if (_state.value.feedbackLanguage == FeedbackLanguage.CHINESE) {
                    FeedbackLanguage.ENGLISH
                } else {
                    FeedbackLanguage.CHINESE
                }
            _state.update { it.copy(feedbackLanguage = next) }
            viewModelScope.launch { settingsStore.setFeedbackLanguage(next) }
        }

        fun dismissError() = _state.update { it.copy(error = null) }

        fun dismissRepeatScore() = _state.update { it.copy(lastRepeatScore = null) }

        // --- internals ---

        private fun bind(session: ConversationSession) {
            val pending =
                session.messages.lastOrNull { it.speaker == Speaker.TUTOR }?.repeatTarget
            _state.update {
                it.copy(
                    session = session,
                    topicTitle = session.title,
                    feedbackLanguage = session.feedbackLanguage,
                    pendingRepeat = pending,
                    phase = ChatPhase.IDLE,
                )
            }
        }

        private fun beginRecognition(phase: ChatPhase) {
            // Don't hard-block on availability: the offline model may still be loading. Start
            // listening and let the recogniser report a precise, retryable status via onError.
            // Clear any previous repeat score so a stale "Keep practising 0%" panel can't linger
            // under a new conversation turn (it is only meaningful for the attempt that produced it).
            _state.update { it.copy(phase = phase, partialTranscript = "", error = null, lastRepeatScore = null) }
            stt.startListening(
                localeTag = "en-GB",
                callback =
                    object : SttCallback {
                        override fun onPartial(text: String) {
                            _state.update { it.copy(partialTranscript = text) }
                        }

                        override fun onResult(text: String) {
                            _state.update { it.copy(partialTranscript = "") }
                            if (listeningForRepeat) {
                                scoreRepeat(text)
                            } else {
                                submit(text)
                            }
                        }

                        override fun onError(message: String) {
                            _state.update { it.copy(phase = ChatPhase.IDLE, error = message) }
                        }
                    },
            )
        }

        private fun submit(text: String) {
            val session = _state.value.session ?: return
            if (text.isBlank()) {
                _state.update { it.copy(phase = ChatPhase.IDLE) }
                return
            }
            // Mask the network round-trip with an instant acknowledgement; the first real
            // sentence is enqueued (QUEUE_ADD) right after it, so playback stays seamless.
            voice.speak(FillerPhrases.random())
            _state.update { it.copy(phase = ChatPhase.THINKING, isBusy = true, streamingReply = "") }

            viewModelScope.launch {
                val chunker = SentenceChunker()
                // Holds the most-recently-completed sentence back by one so the completion
                // callback (which transitions phase back to IDLE) is attached only to the
                // genuinely last utterance, never an interior one.
                var heldSentence: String? = null
                var speakingStarted = false

                // Return to IDLE from either SPEAKING (audio finished) or THINKING (e.g. the TTS
                // engine wasn't ready, so onStart never fired and we never reached SPEAKING) — but
                // never clobber a phase the learner has since moved on to (LISTENING/REPEATING).
                fun settleToIdle() {
                    _state.update {
                        if (it.phase == ChatPhase.SPEAKING || it.phase == ChatPhase.THINKING) {
                            it.copy(phase = ChatPhase.IDLE)
                        } else {
                            it
                        }
                    }
                }

                fun enqueueHeld(onSpoken: (() -> Unit)? = null) {
                    val toSpeak = heldSentence ?: return
                    heldSentence = null
                    voice.enqueue(
                        toSpeak,
                        onStart = {
                            if (!speakingStarted) {
                                speakingStarted = true
                                _state.update { it.copy(phase = ChatPhase.SPEAKING) }
                            }
                        },
                        onDone = { onSpoken?.invoke() },
                    )
                }

                runCatching {
                    sendUtterance.streamInvoke(session, text).collect { event ->
                        when (event) {
                            is SendStreamEvent.ReplyDelta -> {
                                _state.update { it.copy(streamingReply = it.streamingReply + event.text) }
                                chunker.feed(event.text).forEach { sentence ->
                                    enqueueHeld()
                                    heldSentence = sentence
                                }
                            }
                            is SendStreamEvent.Done -> {
                                chunker.flush()?.let { trailing ->
                                    heldSentence = listOfNotNull(heldSentence, trailing).joinToString(" ")
                                }
                                _state.update {
                                    it.copy(
                                        session = event.result.session,
                                        pendingRepeat = event.result.turn.repeatTarget,
                                        isBusy = false,
                                    )
                                }
                                if (heldSentence == null) {
                                    settleToIdle()
                                } else {
                                    enqueueHeld(onSpoken = { settleToIdle() })
                                }
                            }
                        }
                    }
                }.onFailure { error ->
                    _state.update {
                        it.copy(
                            phase = ChatPhase.IDLE,
                            isBusy = false,
                            error = error.message ?: "Something went wrong. Please try again.",
                        )
                    }
                }
            }
        }

        private fun scoreRepeat(text: String) {
            val target = _state.value.pendingRepeat ?: return
            val score = repeatScorer.score(target, text)
            _state.update { it.copy(phase = ChatPhase.IDLE, lastRepeatScore = score) }
        }

        private fun speak(
            text: String,
            andThen: ChatPhase,
        ) {
            _state.update { it.copy(phase = ChatPhase.SPEAKING) }
            voice.speak(
                text = text,
                onDone = { _state.update { if (it.phase == ChatPhase.SPEAKING) it.copy(phase = andThen) else it } },
            )
        }

        private suspend fun currentPreferences(): LearnerPreferences =
            settingsStore.preferences.first()

        override fun onCleared() {
            stt.release()
            voice.release()
            super.onCleared()
        }
    }
