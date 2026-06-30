package com.englishteacher.britspeak

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
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
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

    private class FakeStt(private val result: String) : SpeechToText {
        override val isAvailable = true

        override fun startListening(localeTag: String, callback: SttCallback) {
            callback.onReady()
            callback.onResult(result)
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

    private fun buildViewModel(
        repo: SessionRepository = InMemoryRepo(),
        engine: TutorEngine = engineReturning(TutorTurn("Hi there!", emptyList(), null)),
        stt: SpeechToText = FakeStt("hello"),
    ): ChatViewModel {
        val settings = mockk<SettingsStore>()
        every { settings.preferences } returns flowOf(LearnerPreferences())
        coEvery { settings.setFeedbackLanguage(any()) } returns Unit
        coEvery { settings.setProficiency(any()) } returns Unit
        val apiKey = mockk<ApiKeyStore>()
        every { apiKey.hasKey } returns true

        return ChatViewModel(
            startSession = StartSessionUseCase(clock, ids, repo),
            sendUtterance = SendUtteranceUseCase(engine, clock, ids, repo),
            continueSession = ContinueSessionUseCase(repo),
            repeatScorer = RepeatScorer(),
            stt = stt,
            voice = ImmediateVoice(),
            settingsStore = settings,
            apiKeyStore = apiKey,
            catalog = TopicCatalog,
            dailyTopicSelector = DailyTopicSelector(),
            clock = clock,
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
}
