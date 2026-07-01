package com.englishteacher.core.usecase

import com.englishteacher.core.catalog.TopicCatalog
import com.englishteacher.core.domain.model.Correction
import com.englishteacher.core.domain.model.CorrectionType
import com.englishteacher.core.domain.model.Speaker
import com.englishteacher.core.domain.model.TutorTurn
import com.englishteacher.core.domain.port.LearnerPreferences
import com.englishteacher.core.support.FakeClock
import com.englishteacher.core.support.FakeIdGenerator
import com.englishteacher.core.support.FakeTutorEngine
import com.englishteacher.core.support.InMemorySessionRepository
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class SessionUseCasesTest {
    @Test
    fun `start session creates an opener and persists it`() =
        runTest {
            val repo = InMemorySessionRepository()
            val start = StartSessionUseCase(FakeClock(), FakeIdGenerator(), repo)

            val session = start(TopicCatalog.byId("restaurant")!!, LearnerPreferences())

            assertEquals("restaurant", session.topicId)
            assertEquals(1, session.messages.size)
            assertEquals(Speaker.TUTOR, session.messages.first().speaker)
            assertTrue(session.messages.first().text.isNotBlank())
            assertNotNull(repo.get(session.id))
        }

    @Test
    fun `send utterance records both sides with corrections and repeat target`() =
        runTest {
            val repo = InMemorySessionRepository()
            val start = StartSessionUseCase(FakeClock(), FakeIdGenerator(), repo)
            val session = start(TopicCatalog.freeChat, LearnerPreferences())

            val turn =
                TutorTurn(
                    reply = "Good effort! Where do you work?",
                    corrections =
                        listOf(
                            Correction(
                                original = "I work in a company",
                                corrected = "I work at a company",
                                type = CorrectionType.GRAMMAR,
                                explanationEn = "Use 'at' with company.",
                                explanationZh = "公司前面用 'at'。",
                            ),
                        ),
                    repeatTarget = "I work at a company.",
                )
            val engine = FakeTutorEngine(turn)
            val send = SendUtteranceUseCase(engine, FakeClock(start = 500), FakeIdGenerator(), repo)

            val result = send(session, "I work in a company")

            // User message + tutor message appended to the opener.
            assertEquals(3, result.session.messages.size)
            val user = result.session.messages[1]
            val tutor = result.session.messages[2]
            assertEquals(Speaker.USER, user.speaker)
            assertEquals("I work in a company", user.text)
            assertEquals(Speaker.TUTOR, tutor.speaker)
            assertEquals("Good effort! Where do you work?", tutor.text)
            assertEquals(1, tutor.corrections.size)
            assertEquals("I work at a company.", tutor.repeatTarget)
            assertEquals("I work in a company", engine.lastUtterance)
            // Persisted.
            assertEquals(3, repo.get(session.id)!!.messages.size)
        }

    @Test
    fun `blank utterance is rejected`() =
        runTest {
            val repo = InMemorySessionRepository()
            val session =
                StartSessionUseCase(FakeClock(), FakeIdGenerator(), repo)(
                    TopicCatalog.freeChat,
                    LearnerPreferences(),
                )
            val send =
                SendUtteranceUseCase(
                    FakeTutorEngine(TutorTurn("x", emptyList(), null)),
                    FakeClock(),
                    FakeIdGenerator(),
                    repo,
                )
            assertFailsWith<IllegalArgumentException> { send(session, "   ") }
        }

    @Test
    fun `continue session returns the most recent`() =
        runTest {
            val repo = InMemorySessionRepository()
            val start = StartSessionUseCase(FakeClock(start = 10), FakeIdGenerator(), repo)
            start(TopicCatalog.byId("airport")!!, LearnerPreferences())
            val newer = start(TopicCatalog.byId("doctor")!!, LearnerPreferences())

            val continueUseCase = ContinueSessionUseCase(repo)
            assertEquals(newer.id, continueUseCase.mostRecent()!!.id)
            assertEquals(2, continueUseCase.history().size)
        }

    @Test
    fun `list topics exposes the catalogue`() {
        val topics = ListTopicsUseCase()()
        assertEquals(TopicCatalog.all.size, topics.size)
    }
}
