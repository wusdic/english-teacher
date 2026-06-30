package com.englishteacher.britspeak.di

import android.content.Context
import androidx.room.Room
import com.englishteacher.britspeak.data.KeyedTutorEngine
import com.englishteacher.britspeak.data.db.AppDatabase
import com.englishteacher.britspeak.data.db.RoomSessionRepository
import com.englishteacher.britspeak.data.db.SessionDao
import com.englishteacher.britspeak.speech.AndroidSpeechToText
import com.englishteacher.britspeak.speech.AndroidTutorVoice
import com.englishteacher.britspeak.speech.SpeechToText
import com.englishteacher.britspeak.speech.TutorVoice
import com.englishteacher.core.catalog.TopicCatalog
import com.englishteacher.core.domain.port.Clock
import com.englishteacher.core.domain.port.IdGenerator
import com.englishteacher.core.domain.port.SessionRepository
import com.englishteacher.core.domain.port.TutorEngine
import com.englishteacher.core.usecase.ContinueSessionUseCase
import com.englishteacher.core.usecase.DailyTopicSelector
import com.englishteacher.core.usecase.ListTopicsUseCase
import com.englishteacher.core.usecase.RepeatScorer
import com.englishteacher.core.usecase.SendUtteranceUseCase
import com.englishteacher.core.usecase.StartSessionUseCase
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import okhttp3.OkHttpClient
import java.time.Duration
import java.util.UUID
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class BindingsModule {
    @Binds
    abstract fun bindTutorVoice(impl: AndroidTutorVoice): TutorVoice

    @Binds
    abstract fun bindSpeechToText(impl: AndroidSpeechToText): SpeechToText

    @Binds
    abstract fun bindSessionRepository(impl: RoomSessionRepository): SessionRepository

    @Binds
    abstract fun bindTutorEngine(impl: KeyedTutorEngine): TutorEngine
}

@Module
@InstallIn(SingletonComponent::class)
object AppModule {
    @Provides
    @Singleton
    fun provideDatabase(
        @ApplicationContext context: Context,
    ): AppDatabase =
        Room.databaseBuilder(context, AppDatabase::class.java, AppDatabase.NAME)
            .fallbackToDestructiveMigration()
            .build()

    @Provides
    fun provideSessionDao(db: AppDatabase): SessionDao = db.sessionDao()

    @Provides
    @Singleton
    fun provideHttpClient(): OkHttpClient =
        OkHttpClient.Builder()
            .callTimeout(Duration.ofSeconds(60))
            .build()

    @Provides
    @Singleton
    fun provideClock(): Clock = Clock { System.currentTimeMillis() }

    @Provides
    @Singleton
    fun provideIdGenerator(): IdGenerator = IdGenerator { UUID.randomUUID().toString() }

    @Provides
    fun provideTopicCatalog(): TopicCatalog = TopicCatalog

    @Provides
    fun provideStartSessionUseCase(
        clock: Clock,
        idGenerator: IdGenerator,
        repository: SessionRepository,
    ): StartSessionUseCase = StartSessionUseCase(clock, idGenerator, repository)

    @Provides
    fun provideSendUtteranceUseCase(
        engine: TutorEngine,
        clock: Clock,
        idGenerator: IdGenerator,
        repository: SessionRepository,
    ): SendUtteranceUseCase = SendUtteranceUseCase(engine, clock, idGenerator, repository)

    @Provides
    fun provideContinueSessionUseCase(repository: SessionRepository): ContinueSessionUseCase =
        ContinueSessionUseCase(repository)

    @Provides
    fun provideListTopicsUseCase(): ListTopicsUseCase = ListTopicsUseCase()

    @Provides
    fun provideRepeatScorer(): RepeatScorer = RepeatScorer()

    @Provides
    fun provideDailyTopicSelector(): DailyTopicSelector = DailyTopicSelector()
}
