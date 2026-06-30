package com.englishteacher.britspeak.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.englishteacher.britspeak.data.prefs.ApiKeyStore
import com.englishteacher.britspeak.data.prefs.SettingsStore
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
    val feedbackLanguage: FeedbackLanguage = FeedbackLanguage.CHINESE,
    val proficiency: ProficiencyLevel = ProficiencyLevel.INTERMEDIATE,
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
                _state.update {
                    it.copy(
                        feedbackLanguage = prefs.feedbackLanguage,
                        proficiency = prefs.proficiency,
                        hasApiKey = apiKeyStore.hasKey,
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

        fun setFeedbackLanguage(language: FeedbackLanguage) {
            _state.update { it.copy(feedbackLanguage = language) }
            viewModelScope.launch { settingsStore.setFeedbackLanguage(language) }
        }

        fun setProficiency(level: ProficiencyLevel) {
            _state.update { it.copy(proficiency = level) }
            viewModelScope.launch { settingsStore.setProficiency(level) }
        }

        fun consumeSavedFlash() = _state.update { it.copy(savedFlash = false) }
    }
