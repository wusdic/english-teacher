package com.englishteacher.britspeak.data.prefs

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.englishteacher.core.ai.LlmProvider
import com.englishteacher.core.ai.ProviderConfig
import com.englishteacher.core.domain.model.FeedbackLanguage
import com.englishteacher.core.domain.model.ProficiencyLevel
import com.englishteacher.core.domain.port.LearnerPreferences
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

/** Non-secret model/provider settings (the API key lives in [ApiKeyStore]). */
data class ProviderSettings(
    val provider: LlmProvider = LlmProvider.ANTHROPIC,
    val baseUrl: String = ProviderConfig.ANTHROPIC_BASE_URL,
    val model: String = ProviderConfig.ANTHROPIC_DEFAULT_MODEL,
)

/** Persists user-tunable preferences (correction language, level, model provider, subtitles). */
@Singleton
class SettingsStore
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
    ) {
        private val feedbackKey = stringPreferencesKey("feedback_language")
        private val levelKey = stringPreferencesKey("proficiency")
        private val providerKey = stringPreferencesKey("llm_provider")
        private val baseUrlKey = stringPreferencesKey("llm_base_url")
        private val modelKey = stringPreferencesKey("llm_model")
        private val subtitlesKey = booleanPreferencesKey("subtitles_enabled")

        val preferences: Flow<LearnerPreferences> =
            context.dataStore.data.map { prefs ->
                LearnerPreferences(
                    feedbackLanguage =
                        prefs[feedbackKey]?.let { runCatching { FeedbackLanguage.valueOf(it) }.getOrNull() }
                            ?: FeedbackLanguage.CHINESE,
                    proficiency =
                        prefs[levelKey]?.let { runCatching { ProficiencyLevel.valueOf(it) }.getOrNull() }
                            ?: ProficiencyLevel.INTERMEDIATE,
                )
            }

        val providerSettings: Flow<ProviderSettings> =
            context.dataStore.data.map { prefs ->
                val provider =
                    prefs[providerKey]?.let { runCatching { LlmProvider.valueOf(it) }.getOrNull() }
                        ?: LlmProvider.ANTHROPIC
                val defaults = ProviderConfig.defaultsFor(provider)
                ProviderSettings(
                    provider = provider,
                    baseUrl = prefs[baseUrlKey]?.takeIf { it.isNotBlank() } ?: defaults.baseUrl,
                    model = prefs[modelKey]?.takeIf { it.isNotBlank() } ?: defaults.model,
                )
            }

        val subtitlesEnabled: Flow<Boolean> =
            context.dataStore.data.map { it[subtitlesKey] ?: true }

        suspend fun setFeedbackLanguage(language: FeedbackLanguage) {
            context.dataStore.edit { it[feedbackKey] = language.name }
        }

        suspend fun setProficiency(level: ProficiencyLevel) {
            context.dataStore.edit { it[levelKey] = level.name }
        }

        /** Switches provider and resets base URL + model to that provider's defaults. */
        suspend fun setProvider(provider: LlmProvider) {
            val defaults = ProviderConfig.defaultsFor(provider)
            context.dataStore.edit {
                it[providerKey] = provider.name
                it[baseUrlKey] = defaults.baseUrl
                it[modelKey] = defaults.model
            }
        }

        suspend fun setBaseUrl(baseUrl: String) {
            context.dataStore.edit { it[baseUrlKey] = baseUrl.trim() }
        }

        suspend fun setModel(model: String) {
            context.dataStore.edit { it[modelKey] = model.trim() }
        }

        suspend fun setSubtitlesEnabled(enabled: Boolean) {
            context.dataStore.edit { it[subtitlesKey] = enabled }
        }
    }
