package com.englishteacher.britspeak.ui.history

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.englishteacher.britspeak.data.db.RoomSessionRepository
import com.englishteacher.core.domain.model.ConversationSession
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class HistoryViewModel
    @Inject
    constructor(
        private val repository: RoomSessionRepository,
    ) : ViewModel() {
        val sessions: StateFlow<List<ConversationSession>> =
            repository.observeAll()
                .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

        fun delete(id: String) {
            viewModelScope.launch { repository.delete(id) }
        }
    }
