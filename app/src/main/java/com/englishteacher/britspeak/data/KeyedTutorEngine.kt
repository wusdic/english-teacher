package com.englishteacher.britspeak.data

import com.englishteacher.britspeak.data.prefs.ApiKeyStore
import com.englishteacher.britspeak.data.prefs.SettingsStore
import com.englishteacher.core.ai.ProviderConfig
import com.englishteacher.core.ai.TutorEngineFactory
import com.englishteacher.core.domain.model.ConversationSession
import com.englishteacher.core.domain.model.TutorTurn
import com.englishteacher.core.domain.port.TutorEngine
import com.englishteacher.core.domain.port.TutorEngineException
import kotlinx.coroutines.flow.first
import okhttp3.OkHttpClient
import javax.inject.Inject
import javax.inject.Singleton

/**
 * A [TutorEngine] that resolves the current provider settings + API key on each call, so changing
 * the provider, model, base URL or key in Settings takes effect immediately. Throws a clear
 * [TutorEngineException] when no key is configured.
 */
@Singleton
class KeyedTutorEngine
    @Inject
    constructor(
        private val apiKeyStore: ApiKeyStore,
        private val settingsStore: SettingsStore,
        private val httpClient: OkHttpClient,
    ) : TutorEngine {
        @Volatile private var cached: Pair<ProviderConfig, TutorEngine>? = null

        private suspend fun engine(): TutorEngine {
            val key =
                apiKeyStore.apiKey
                    ?: throw TutorEngineException("No API key configured. Add one in Settings.")
            val settings = settingsStore.providerSettings.first()
            val config =
                ProviderConfig(
                    provider = settings.provider,
                    apiKey = key,
                    model = settings.model,
                    baseUrl = settings.baseUrl,
                )
            cached?.let { (cachedConfig, engine) -> if (cachedConfig == config) return engine }
            val engine = TutorEngineFactory.create(config, httpClient)
            cached = config to engine
            return engine
        }

        override suspend fun respond(
            session: ConversationSession,
            userUtterance: String,
        ): TutorTurn = engine().respond(session, userUtterance)

        override suspend fun opener(session: ConversationSession): TutorTurn = engine().opener(session)
    }
