package com.englishteacher.britspeak.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.englishteacher.britspeak.data.prefs.ApiKeyStore
import com.englishteacher.britspeak.data.prefs.SettingsStore
import com.englishteacher.core.ai.LlmProvider
import com.englishteacher.core.domain.model.FeedbackLanguage
import com.englishteacher.core.domain.model.ProficiencyLevel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class SettingsUiState(
    val hasApiKey: Boolean = false,
    val provider: LlmProvider = LlmProvider.ANTHROPIC,
    val baseUrl: String = "",
    val model: String = "",
    val feedbackLanguage: FeedbackLanguage = FeedbackLanguage.CHINESE,
    val proficiency: ProficiencyLevel = ProficiencyLevel.INTERMEDIATE,
    val subtitlesEnabled: Boolean = true,
    val savedFlash: Boolean = false,
)

@HiltViewModel
class SettingsViewModel
    @Inject
    constructor(
        private val settingsStore: SettingsStore,
        private val apiKeyStore: ApiKeyStore,
    ) : ViewModel() {
        private val _state = MutableStateFlow(SettingsUiState(hasApiKey = apiKeyStore.hasKey))
        val state: StateFlow<SettingsUiState> = _state.asStateFlow()

        init {
            viewModelScope.launch {
                val prefs = settingsStore.preferences.first()
                val provider = settingsStore.providerSettings.first()
                val subtitles = settingsStore.subtitlesEnabled.first()
                _state.update {
                    it.copy(
                        feedbackLanguage = prefs.feedbackLanguage,
                        proficiency = prefs.proficiency,
                        hasApiKey = apiKeyStore.hasKey,
                        provider = provider.provider,
                        baseUrl = provider.baseUrl,
                        model = provider.model,
                        subtitlesEnabled = subtitles,
                    )
                }
            }
        }

        fun saveApiKey(key: String) {
            apiKeyStore.apiKey = key
            _state.update { it.copy(hasApiKey = apiKeyStore.hasKey, savedFlash = true) }
        }

        fun clearApiKey() {
            apiKeyStore.apiKey = null
            _state.update { it.copy(hasApiKey = false) }
        }

        fun setProvider(provider: LlmProvider) {
            viewModelScope.launch {
                settingsStore.setProvider(provider)
                val updated = settingsStore.providerSettings.first()
                _state.update {
                    it.copy(provider = updated.provider, baseUrl = updated.baseUrl, model = updated.model)
                }
            }
        }

        fun setBaseUrl(baseUrl: String) {
            _state.update { it.copy(baseUrl = baseUrl) }
            viewModelScope.launch { settingsStore.setBaseUrl(baseUrl) }
        }

        fun setModel(model: String) {
            _state.update { it.copy(model = model) }
            viewModelScope.launch { settingsStore.setModel(model) }
        }

        fun setFeedbackLanguage(language: FeedbackLanguage) {
            _state.update { it.copy(feedbackLanguage = language) }
            viewModelScope.launch { settingsStore.setFeedbackLanguage(language) }
        }

        fun setProficiency(level: ProficiencyLevel) {
            _state.update { it.copy(proficiency = level) }
            viewModelScope.launch { settingsStore.setProficiency(level) }
        }

        fun setSubtitlesEnabled(enabled: Boolean) {
            _state.update { it.copy(subtitlesEnabled = enabled) }
            viewModelScope.launch { settingsStore.setSubtitlesEnabled(enabled) }
        }

        fun consumeSavedFlash() = _state.update { it.copy(savedFlash = false) }
    }
