package com.rootrecord.kilauea.alerts.ui.feedback

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.rootrecord.kilauea.alerts.data.local.KilaueaPreferences
import com.rootrecord.kilauea.alerts.data.repository.FeedbackRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class FeedbackViewModel @Inject constructor(
    private val repo: FeedbackRepository,
    prefs: KilaueaPreferences,
) : ViewModel() {

    val signedIn = prefs.authSignedIn.stateIn(viewModelScope, SharingStarted.Eagerly, false)

    private val _sending = MutableStateFlow(false)
    val sending = _sending.asStateFlow()

    private val _toast = MutableStateFlow<String?>(null)
    val toast = _toast.asStateFlow()

    fun consumeToast() {
        _toast.value = null
    }

    fun send(type: String, message: String, replyEmail: String?, includeDiagnostics: Boolean) {
        viewModelScope.launch {
            _toast.value = null
            if (!signedIn.value) {
                _toast.value = "Sign in from Menu to submit feedback."
                return@launch
            }
            if (message.isBlank()) {
                _toast.value = "Please enter your feedback."
                return@launch
            }
            _sending.value = true
            val r = repo.send(type, message, replyEmail, includeDiagnostics)
            _sending.value = false
            r.onSuccess { _toast.value = "Feedback sent — thank you." }
            r.onFailure { _toast.value = it.message ?: "Could not send feedback." }
        }
    }
}
