package com.rootrecord.rootmc.ui.realm

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.rootrecord.rootmc.data.repository.RealmGroupMessage
import com.rootrecord.rootmc.data.repository.RealmRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class GroupChatUiState(
    val groupId: String = "",
    val messages: List<RealmGroupMessage> = emptyList(),
    val draft: String = "",
    val loading: Boolean = false,
    val sending: Boolean = false,
    val error: String? = null,
)

@HiltViewModel
class GroupChatViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val realmRepository: RealmRepository,
) : ViewModel() {

    private val groupId: String = checkNotNull(savedStateHandle.get<String>("groupId"))

    private val _uiState = MutableStateFlow(GroupChatUiState(groupId = groupId))
    val uiState: StateFlow<GroupChatUiState> = _uiState.asStateFlow()

    init {
        loadMessages()
    }

    fun loadMessages() {
        viewModelScope.launch {
            _uiState.update { it.copy(loading = true, error = null) }
            realmRepository.listGroupMessages(groupId)
                .onSuccess { msgs -> _uiState.update { it.copy(loading = false, messages = msgs) } }
                .onFailure { err -> _uiState.update { it.copy(loading = false, error = err.message) } }
        }
    }

    fun updateDraft(value: String) = _uiState.update { it.copy(draft = value) }

    fun sendMessage() {
        val body = _uiState.value.draft.trim()
        if (body.isEmpty()) return
        viewModelScope.launch {
            _uiState.update { it.copy(sending = true) }
            realmRepository.sendGroupMessage(groupId, body)
                .onSuccess {
                    _uiState.update { it.copy(sending = false, draft = "") }
                    loadMessages()
                }
                .onFailure { err -> _uiState.update { it.copy(sending = false, error = err.message) } }
        }
    }
}
