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

        // Continuous "hands-free" conversation: once on, the app keeps listening again after each
        // tutor reply until the learner taps the button a second time to stop.
        private var conversationActive = false

        // Guards against a late recogniser result (Vosk flushes one when stopped) being submitted
        // after the learner has already tapped stop; also enforces one result per listen.
        private var acceptingResults = false

        // True when the filler acknowledgement was already spoken at end-of-capture (Whisper
        // signals onEndOfSpeech before transcription), so submit() must not speak a second one.
        private var fillerAlreadySpoken = false

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

        /**
         * Enter hands-free continuous conversation: start listening now, and keep listening again
         * after every tutor reply until [stopConversation] is called. Safe to call repeatedly.
         */
        fun startConversation() {
            if (conversationActive) return
            if (!_state.value.hasApiKey) {
                _state.update { it.copy(error = "请先在设置里配置大模型 API Key。") }
                return
            }
            conversationActive = true
            _state.update { it.copy(conversationActive = true) }
            // If the tutor is mid-speech (e.g. the opener), don't interrupt — we'll auto-listen
            // when it finishes via continueOrIdle().
            if (_state.value.canSpeak) startListening()
        }

        /** Stop continuous conversation: stop listening now (the current reply still finishes). */
        fun stopConversation() {
            conversationActive = false
            acceptingResults = false // ignore any final result the recogniser flushes on stop
            _state.update { it.copy(conversationActive = false) }
            stt.stopListening()
            _state.update {
                if (it.phase == ChatPhase.LISTENING || it.phase == ChatPhase.REPEATING) {
                    it.copy(phase = ChatPhase.IDLE)
                } else {
                    it
                }
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

        /** After a turn ends: settle to IDLE and, in continuous mode, listen again shortly. */
        private fun continueOrIdle() {
            _state.update {
                if (it.phase == ChatPhase.SPEAKING || it.phase == ChatPhase.THINKING) {
                    it.copy(phase = ChatPhase.IDLE)
                } else {
                    it
                }
            }
            maybeContinueListening()
        }

        /** In continuous mode, resume listening after a short beat once we're idle. */
        private fun maybeContinueListening() {
            if (!conversationActive) return
            viewModelScope.launch {
                delay(CONTINUOUS_RELISTEN_DELAY_MS)
                if (conversationActive && _state.value.phase == ChatPhase.IDLE && _state.value.canSpeak) {
                    startListening()
                }
            }
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
            acceptingResults = true
            fillerAlreadySpoken = false
            _state.update { it.copy(phase = phase, partialTranscript = "", error = null, lastRepeatScore = null) }
            stt.startListening(
                localeTag = "en-GB",
                callback =
                    object : SttCallback {
                        override fun onPartial(text: String) {
                            if (acceptingResults) _state.update { it.copy(partialTranscript = text) }
                        }

                        override fun onEndOfSpeech() {
                            // Whisper signals this the moment capture ends, BEFORE transcription.
                            // Acknowledge instantly and show "thinking" so the recognition delay
                            // is masked by voice instead of an awkward silent wait.
                            if (!acceptingResults || listeningForRepeat) return
                            fillerAlreadySpoken = true
                            voice.speak(FillerPhrases.random())
                            _state.update { it.copy(phase = ChatPhase.THINKING, partialTranscript = "") }
                        }

                        override fun onResult(text: String) {
                            if (!acceptingResults) return
                            acceptingResults = false
                            _state.update { it.copy(partialTranscript = "") }
                            if (listeningForRepeat) {
                                scoreRepeat(text)
                            } else {
                                submit(text)
                            }
                        }

                        override fun onError(message: String) {
                            acceptingResults = false
                            // Also leave hands-free mode so the button state matches reality
                            // (we are no longer listening and won't auto-restart into the error).
                            conversationActive = false
                            _state.update {
                                it.copy(phase = ChatPhase.IDLE, conversationActive = false, error = message)
                            }
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
            // Mask the network round-trip with an instant acknowledgement — unless one was
            // already spoken at end-of-capture (see onEndOfSpeech); the first real sentence is
            // enqueued (QUEUE_ADD) right after it, so playback stays seamless.
            if (!fillerAlreadySpoken) voice.speak(FillerPhrases.random())
            fillerAlreadySpoken = false
            _state.update { it.copy(phase = ChatPhase.THINKING, isBusy = true, streamingReply = "") }

            viewModelScope.launch {
                val chunker = SentenceChunker()
                // Holds the most-recently-completed sentence back by one so the completion
                // callback (which transitions phase back to IDLE) is attached only to the
                // genuinely last utterance, never an interior one.
                var heldSentence: String? = null
                var speakingStarted = false

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
                                    continueOrIdle()
                                } else {
                                    enqueueHeld(onSpoken = { continueOrIdle() })
                                }
                            }
                        }
                    }
                }.onFailure { error ->
                    // Stop the hands-free loop on failure so we don't retry into the same error.
                    conversationActive = false
                    _state.update {
                        it.copy(
                            phase = ChatPhase.IDLE,
                            isBusy = false,
                            conversationActive = false,
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
            maybeContinueListening()
        }

        private fun speak(
            text: String,
            andThen: ChatPhase,
        ) {
            _state.update { it.copy(phase = ChatPhase.SPEAKING) }
            voice.speak(
                text = text,
                onDone = {
                    if (andThen == ChatPhase.IDLE) {
                        // Route opener/replay completion through the same path so continuous mode
                        // also auto-listens once the tutor stops speaking.
                        continueOrIdle()
                    } else {
                        _state.update { if (it.phase == ChatPhase.SPEAKING) it.copy(phase = andThen) else it }
                    }
                },
            )
        }

        private suspend fun currentPreferences(): LearnerPreferences =
            settingsStore.preferences.first()

        override fun onCleared() {
            stt.release()
            voice.release()
            super.onCleared()
        }

        private companion object {
            /** Small beat after the tutor stops speaking before listening again in continuous mode. */
            const val CONTINUOUS_RELISTEN_DELAY_MS = 350L
        }
    }
