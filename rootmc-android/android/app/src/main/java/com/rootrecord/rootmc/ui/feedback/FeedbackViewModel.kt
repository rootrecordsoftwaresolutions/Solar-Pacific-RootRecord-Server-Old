package com.rootrecord.rootmc.ui.feedback

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.rootrecord.rootmc.data.local.RootMcPreferences
import com.rootrecord.rootmc.data.repository.FeedbackRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class FeedbackUiState(
    val type: String = "general",
    val message: String = "",
    val replyEmail: String = "",
    val includeDiagnostics: Boolean = true,
    val sending: Boolean = false,
    val success: Boolean = false,
    val error: String? = null,
)

@HiltViewModel
class FeedbackViewModel @Inject constructor(
    private val feedbackRepository: FeedbackRepository,
    private val prefs: RootMcPreferences,
) : ViewModel() {

    private val _uiState = MutableStateFlow(FeedbackUiState())
    val uiState: StateFlow<FeedbackUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            prefs.ensureGuestId()
        }
    }

    fun updateType(value: String) = _uiState.update { it.copy(type = value, error = null) }
    fun updateMessage(value: String) = _uiState.update { it.copy(message = value, error = null) }
    fun updateReplyEmail(value: String) = _uiState.update { it.copy(replyEmail = value) }
    fun updateDiagnostics(value: Boolean) = _uiState.update { it.copy(includeDiagnostics = value) }

    fun send() {
        val state = _uiState.value
        if (state.message.trim().length < 8) {
            _uiState.update { it.copy(error = "Please enter at least 8 characters.") }
            return
        }
        viewModelScope.launch {
            _uiState.update { it.copy(sending = true, error = null) }
            prefs.ensureGuestId()
            val result = feedbackRepository.send(
                type = state.type,
                message = state.message,
                replyEmail = state.replyEmail.takeIf { it.isNotBlank() },
                includeDiagnostics = state.includeDiagnostics,
            )
            _uiState.update {
                it.copy(
                    sending = false,
                    success = result.isSuccess,
                    error = result.exceptionOrNull()?.message,
                )
            }
        }
    }

    fun resetSuccess() = _uiState.update { it.copy(success = false) }
}
