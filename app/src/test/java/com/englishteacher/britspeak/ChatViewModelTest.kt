package com.englishteacher.britspeak

import com.englishteacher.britspeak.data.CustomTopicHolder
import com.englishteacher.britspeak.data.prefs.ApiKeyStore
import com.englishteacher.britspeak.data.prefs.SettingsStore
import com.englishteacher.britspeak.speech.SpeechToText
import com.englishteacher.britspeak.speech.SttCallback
import com.englishteacher.britspeak.speech.TutorVoice
import com.englishteacher.britspeak.ui.chat.ChatPhase
import com.englishteacher.britspeak.ui.chat.ChatViewModel
import com.englishteacher.core.catalog.TopicCatalog
import com.englishteacher.core.domain.model.ConversationSession
import com.englishteacher.core.domain.model.Correction
import com.englishteacher.core.domain.model.CorrectionType
import com.englishteacher.core.domain.model.FeedbackLanguage
import com.englishteacher.core.domain.model.TutorStreamEvent
import com.englishteacher.core.domain.model.TutorTurn
import com.englishteacher.core.domain.port.Clock
import com.englishteacher.core.domain.port.IdGenerator
import com.englishteacher.core.domain.port.LearnerPreferences
import com.englishteacher.core.domain.port.SessionRepository
import com.englishteacher.core.domain.port.TutorEngine
import com.englishteacher.core.usecase.ContinueSessionUseCase
import com.englishteacher.core.usecase.DailyTopicSelector
import com.englishteacher.core.usecase.RepeatScorer
import com.englishteacher.core.usecase.SendUtteranceUseCase
import com.englishteacher.core.usecase.StartSessionUseCase
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class ChatViewModelTest {
    @get:Rule val mainDispatcherRule = MainDispatcherRule()

    private class InMemoryRepo : SessionRepository {
        private val store = LinkedHashMap<String, ConversationSession>()

        override suspend fun save(session: ConversationSession) {
            store[session.id] = session
        }

        override suspend fun get(id: String) = store[id]

        override suspend fun all() = store.values.sortedByDescending { it.updatedAtMillis }

        override suspend fun mostRecent() = all().firstOrNull()

        override suspend fun delete(id: String) {
            store.remove(id)
        }
    }

    /** Voice that completes immediately so the phase returns to IDLE in tests. */
    private class ImmediateVoice : TutorVoice {
        override fun speak(text: String, onStart: () -> Unit, onDone: () -> Unit) {
            onStart()
            onDone()
        }

        override fun stop() {}

        override fun release() {}
    }

    /**
     * Voice that fires only [onDone] and never [onStart] — mirrors [AndroidTutorVoice]'s behaviour
     * when the TTS engine isn't ready yet (it posts onDone via a fallback without ever starting).
     */
    private class DoneOnlyVoice : TutorVoice {
        override fun speak(text: String, onStart: () -> Unit, onDone: () -> Unit) {
            onDone()
        }

        override fun enqueue(text: String, onStart: () -> Unit, onDone: () -> Unit) {
            onDone()
        }

        override fun stop() {}

        override fun release() {}
    }

    private class FakeStt(private val result: String) : SpeechToText {
        override val isAvailable = true

        override fun startListening(localeTag: String, callback: SttCallback) {
            callback.onReady()
            callback.onResult(result)
        }

        override fun stopListening() {}

        override fun release() {}
    }

    /** Whisper-style STT: signals end-of-capture (onEndOfSpeech) BEFORE delivering the result. */
    private class EndOfSpeechFirstStt(private val result: String) : SpeechToText {
        override val isAvailable = true

        override fun startListening(localeTag: String, callback: SttCallback) {
            callback.onReady()
            callback.onEndOfSpeech()
            callback.onResult(result)
        }

        override fun stopListening() {}

        override fun release() {}
    }

    /** Voice that records every spoken text so tests can assert on filler behaviour. */
    private class RecordingVoice : TutorVoice {
        val spoken = mutableListOf<String>()

        override fun speak(text: String, onStart: () -> Unit, onDone: () -> Unit) {
            spoken.add(text)
            onStart()
            onDone()
        }

        override fun enqueue(text: String, onStart: () -> Unit, onDone: () -> Unit) {
            spoken.add(text)
            onStart()
            onDone()
        }

        override fun stop() {}

        override fun release() {}
    }

    /** STT that starts listening but never returns a result — for testing state toggles safely. */
    private class IdleStt : SpeechToText {
        override val isAvailable = true

        override fun startListening(localeTag: String, callback: SttCallback) {
            callback.onReady()
        }

        override fun stopListening() {}

        override fun release() {}
    }

    /** STT that returns [result] only on the first listen, then stays idle (avoids a test loop). */
    private class OneShotStt(private val result: String) : SpeechToText {
        override val isAvailable = true
        private var fired = false

        override fun startListening(localeTag: String, callback: SttCallback) {
            callback.onReady()
            if (!fired) {
                fired = true
                callback.onResult(result)
            }
        }

        override fun stopListening() {}

        override fun release() {}
    }

    private val clock = Clock { 1_000L }
    private val ids = object : IdGenerator {
        private var n = 0
        override fun newId() = "id-${n++}"
    }

    private fun engineReturning(turn: TutorTurn): TutorEngine =
        object : TutorEngine {
            override suspend fun respond(session: ConversationSession, userUtterance: String) = turn
        }

    /** [TutorEngine] that streams [deltas] as separate events before completing with [turn]. */
    private fun streamingEngine(
        deltas: List<String>,
        turn: TutorTurn,
    ): TutorEngine =
        object : TutorEngine {
            override suspend fun respond(session: ConversationSession, userUtterance: String) = turn

            override fun streamRespond(
                session: ConversationSession,
                userUtterance: String,
            ): Flow<TutorStreamEvent> =
                flow {
                    deltas.forEach { emit(TutorStreamEvent.ReplyDelta(it)) }
                    emit(TutorStreamEvent.Done(turn))
                }
        }

    private fun buildViewModel(
        repo: SessionRepository = InMemoryRepo(),
        engine: TutorEngine = engineReturning(TutorTurn("Hi there!", emptyList(), null)),
        stt: SpeechToText = FakeStt("hello"),
        voice: TutorVoice = ImmediateVoice(),
        hasApiKey: Boolean = true,
    ): ChatViewModel {
        val settings = mockk<SettingsStore>()
        every { settings.preferences } returns flowOf(LearnerPreferences())
        every { settings.subtitlesEnabled } returns flowOf(true)
        coEvery { settings.setFeedbackLanguage(any()) } returns Unit
        coEvery { settings.setProficiency(any()) } returns Unit
        coEvery { settings.setSubtitlesEnabled(any()) } returns Unit
        val apiKey = mockk<ApiKeyStore>()
        every { apiKey.hasKey } returns hasApiKey

        return ChatViewModel(
            startSession = StartSessionUseCase(clock, ids, repo),
            sendUtterance = SendUtteranceUseCase(engine, clock, ids, repo),
            continueSession = ContinueSessionUseCase(repo),
            repeatScorer = RepeatScorer(),
            stt = stt,
            voice = voice,
            settingsStore = settings,
            apiKeyStore = apiKey,
            catalog = TopicCatalog,
            dailyTopicSelector = DailyTopicSelector(),
            clock = clock,
            customTopicHolder = CustomTopicHolder(),
        )
    }

    @Test
    fun `initial state reads preferences and api key`() =
        runTest {
            val vm = buildViewModel()
            assertEquals(FeedbackLanguage.CHINESE, vm.state.value.feedbackLanguage)
            assertTrue(vm.state.value.hasApiKey)
        }

    @Test
    fun `toggling feedback language flips between chinese and english`() =
        runTest {
            val vm = buildViewModel()
            vm.toggleFeedbackLanguage()
            assertEquals(FeedbackLanguage.ENGLISH, vm.state.value.feedbackLanguage)
            vm.toggleFeedbackLanguage()
            assertEquals(FeedbackLanguage.CHINESE, vm.state.value.feedbackLanguage)
        }

    @Test
    fun `starting a topic binds a session with an opener and returns to idle`() =
        runTest {
            val vm = buildViewModel()
            vm.startOnTopic("restaurant")
            assertEquals("At a Restaurant", vm.state.value.topicTitle)
            assertEquals(1, vm.state.value.session?.messages?.size)
            assertEquals(ChatPhase.IDLE, vm.state.value.phase)
        }

    @Test
    fun `starting a custom topic id with no stashed topic falls back to free chat`() =
        runTest {
            // Defensive: if the user somehow lands on a custom topicId without going through the
            // picker (e.g. process death), the app must not crash — it starts free chat instead.
            val vm = buildViewModel()
            vm.startOnTopic("custom_123")
            assertEquals("Free Chat", vm.state.value.topicTitle)
        }

    @Test
    fun `speaking a turn appends messages and exposes the repeat target`() =
        runTest {
            val turn =
                TutorTurn(
                    reply = "Great! What else?",
                    corrections =
                        listOf(
                            Correction(
                                "i goed",
                                "I went",
                                CorrectionType.GRAMMAR,
                                "Past tense of go is went.",
                                "go 的过去式是 went。",
                            ),
                        ),
                    repeatTarget = "I went to the shop.",
                )
            val vm = buildViewModel(engine = engineReturning(turn), stt = FakeStt("i goed to the shop"))
            vm.startOnTopic("free_chat")
            vm.startListening() // FakeStt immediately returns a result → triggers submit

            val state = vm.state.value
            assertEquals("I went to the shop.", state.pendingRepeat)
            // opener + user + tutor
            assertEquals(3, state.session?.messages?.size)
            assertEquals(ChatPhase.IDLE, state.phase)
        }

    @Test
    fun `a reply streamed across multiple deltas still ends idle with the final session`() =
        runTest {
            val turn =
                TutorTurn(
                    reply = "Lovely! Anything to drink?",
                    corrections = emptyList(),
                    repeatTarget = "Anything to drink?",
                )
            val vm =
                buildViewModel(
                    engine = streamingEngine(deltas = listOf("Lovely! ", "Anything to drink?"), turn = turn),
                    stt = FakeStt("I'll have the soup"),
                )
            vm.startOnTopic("free_chat")
            vm.startListening()

            val state = vm.state.value
            assertEquals("Anything to drink?", state.pendingRepeat)
            assertEquals(3, state.session?.messages?.size)
            assertEquals(ChatPhase.IDLE, state.phase)
        }

    @Test
    fun `starting a new turn clears a previous repeat score so it cannot linger`() =
        runTest {
            // Regression: a scored repeat used to stick under every later message because
            // lastRepeatScore was never cleared, so a normal "hello" appeared to be answered with
            // an old "Keep practising 0%" panel.
            val turn = TutorTurn("Nice! Try: say this please.", emptyList(), "say this please")
            val vm =
                buildViewModel(
                    engine = streamingEngine(deltas = listOf("Nice! Try: say this please."), turn = turn),
                    stt = FakeStt("say this please"),
                )
            vm.startOnTopic("free_chat")
            vm.startListening() // normal turn → tutor turn exposes a repeat target
            assertEquals("say this please", vm.state.value.pendingRepeat)

            vm.startRepeat() // produces a repeat score panel
            assertNotNull(vm.state.value.lastRepeatScore)

            vm.startListening() // a new normal turn must clear the stale score
            assertNull(vm.state.value.lastRepeatScore)
        }

    @Test
    fun `start conversation turns on continuous mode and begins listening`() =
        runTest {
            val vm = buildViewModel(stt = IdleStt())
            vm.startOnTopic("free_chat")

            vm.startConversation()
            assertTrue(vm.state.value.conversationActive)
            assertEquals(ChatPhase.LISTENING, vm.state.value.phase)
        }

    @Test
    fun `stop conversation turns off continuous mode and returns to idle`() =
        runTest {
            val vm = buildViewModel(stt = IdleStt())
            vm.startOnTopic("free_chat")
            vm.startConversation()

            vm.stopConversation()
            assertFalse(vm.state.value.conversationActive)
            assertEquals(ChatPhase.IDLE, vm.state.value.phase)
        }

    @Test
    fun `start conversation without an api key surfaces an error and stays off`() =
        runTest {
            val vm = buildViewModel(stt = IdleStt(), hasApiKey = false)
            vm.startConversation()
            assertFalse(vm.state.value.conversationActive)
            assertNotNull(vm.state.value.error)
        }

    @Test
    fun `continuous mode listens again after a tutor reply completes`() =
        runTest {
            // The heart of the feature: one spoken turn should record the exchange and then the
            // app should be listening again automatically, still in continuous mode.
            val vm =
                buildViewModel(
                    engine = streamingEngine(deltas = listOf("Nice to meet you!"), turn = TutorTurn("Nice to meet you!", emptyList(), null)),
                    stt = OneShotStt("hello"),
                )
            vm.startOnTopic("free_chat")
            vm.startConversation() // listens → OneShot returns "hello" → full turn runs
            advanceUntilIdle() // let the post-reply re-listen delay elapse

            assertTrue(vm.state.value.conversationActive)
            assertEquals(ChatPhase.LISTENING, vm.state.value.phase)
            // opener + user(hello) + tutor(reply)
            assertEquals(3, vm.state.value.session?.messages?.size)
        }

    @Test
    fun `phase returns to idle even if the tts engine never fires onStart`() =
        runTest {
            // Regression: when TTS isn't ready it fires only onDone (never onStart), so the phase
            // never reaches SPEAKING. The turn must still settle back to IDLE, not stick in THINKING.
            val turn = TutorTurn("All good, carry on!", emptyList(), "Carry on.")
            val vm =
                buildViewModel(
                    engine = streamingEngine(deltas = listOf("All good, carry on!"), turn = turn),
                    stt = FakeStt("okay"),
                    voice = DoneOnlyVoice(),
                )
            vm.startOnTopic("free_chat")
            vm.startListening()

            assertEquals(ChatPhase.IDLE, vm.state.value.phase)
        }

    @Test
    fun `whisper end-of-speech speaks exactly one filler and the turn still completes`() =
        runTest {
            // Whisper signals onEndOfSpeech before its (slow) transcription; the filler must play
            // right then to mask the delay, and submit() must not add a second filler.
            val voice = RecordingVoice()
            val turn = TutorTurn("Nice to meet you!", emptyList(), null)
            val vm =
                buildViewModel(
                    engine = streamingEngine(deltas = listOf("Nice to meet you!"), turn = turn),
                    stt = EndOfSpeechFirstStt("hello what's your name"),
                    voice = voice,
                )
            vm.startOnTopic("free_chat")
            vm.startListening()

            assertEquals(ChatPhase.IDLE, vm.state.value.phase)
            assertEquals(3, vm.state.value.session?.messages?.size)
            // opener + exactly one filler + the reply sentence — no double acknowledgement.
            assertEquals(3, voice.spoken.size)
        }
}
