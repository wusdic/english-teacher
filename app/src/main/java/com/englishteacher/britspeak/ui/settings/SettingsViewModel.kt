package com.englishteacher.britspeak.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.englishteacher.britspeak.data.prefs.ApiKeyStore
import com.englishteacher.britspeak.data.prefs.SettingsStore
import com.englishteacher.core.ai.ConnectionTestResult
import com.englishteacher.core.ai.ConnectionTester
import com.englishteacher.core.ai.LlmProvider
import com.englishteacher.core.ai.ProviderConfig
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
    val testing: Boolean = false,
    val testSuccess: Boolean? = null,
    val testMessage: String? = null,
)

@HiltViewModel
class SettingsViewModel
    @Inject
    constructor(
        private val settingsStore: SettingsStore,
        private val apiKeyStore: ApiKeyStore,
        private val connectionTester: ConnectionTester,
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

        /** Runs a live probe against the configured provider/model/key and reports the result. */
        fun testConnection() {
            val key = apiKeyStore.apiKey
            if (key.isNullOrBlank()) {
                _state.update {
                    it.copy(testing = false, testSuccess = false, testMessage = "请先保存 API key")
                }
                return
            }
            val snapshot = _state.value
            val config =
                ProviderConfig(
                    provider = snapshot.provider,
                    apiKey = key,
                    model = snapshot.model,
                    baseUrl = snapshot.baseUrl,
                )
            _state.update { it.copy(testing = true, testSuccess = null, testMessage = null) }
            viewModelScope.launch {
                val result = connectionTester.test(config)
                _state.update {
                    when (result) {
                        is ConnectionTestResult.Success ->
                            it.copy(
                                testing = false,
                                testSuccess = true,
                                testMessage = "连接正常 ✅ 模型回复：${result.sample}",
                            )
                        is ConnectionTestResult.Failure ->
                            it.copy(
                                testing = false,
                                testSuccess = false,
                                testMessage = "连接失败：${result.message}",
                            )
                    }
                }
            }
        }

        fun consumeSavedFlash() = _state.update { it.copy(savedFlash = false) }
    }
