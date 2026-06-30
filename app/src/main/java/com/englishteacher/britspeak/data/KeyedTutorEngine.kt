package com.englishteacher.britspeak.data

import com.englishteacher.britspeak.data.prefs.ApiKeyStore
import com.englishteacher.core.ai.AnthropicConfig
import com.englishteacher.core.ai.AnthropicTutorEngine
import com.englishteacher.core.domain.model.ConversationSession
import com.englishteacher.core.domain.model.TutorTurn
import com.englishteacher.core.domain.port.TutorEngine
import com.englishteacher.core.domain.port.TutorEngineException
import okhttp3.OkHttpClient
import javax.inject.Inject
import javax.inject.Singleton

/**
 * A [TutorEngine] that resolves the user's API key lazily on each call, so changing the key in
 * Settings takes effect immediately without rebuilding the dependency graph. Throws a clear
 * [TutorEngineException] when no key is configured.
 */
@Singleton
class KeyedTutorEngine
    @Inject
    constructor(
        private val apiKeyStore: ApiKeyStore,
        private val httpClient: OkHttpClient,
    ) : TutorEngine {
        @Volatile private var cached: Pair<String, AnthropicTutorEngine>? = null

        private fun engine(): AnthropicTutorEngine {
            val key =
                apiKeyStore.apiKey
                    ?: throw TutorEngineException("No Anthropic API key configured. Add one in Settings.")
            cached?.let { (cachedKey, engine) -> if (cachedKey == key) return engine }
            val engine =
                AnthropicTutorEngine(
                    config = AnthropicConfig(apiKey = key),
                    httpClient = httpClient,
                )
            cached = key to engine
            return engine
        }

        override suspend fun respond(
            session: ConversationSession,
            userUtterance: String,
        ): TutorTurn = engine().respond(session, userUtterance)

        override suspend fun opener(session: ConversationSession): TutorTurn = engine().opener(session)
    }
