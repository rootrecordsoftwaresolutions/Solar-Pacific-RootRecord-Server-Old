package com.rootrecord.kilauea.alerts.ui.livefeeds

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.rootrecord.kilauea.alerts.data.local.KilaueaPreferences
import com.rootrecord.kilauea.alerts.data.repository.LiveFeedsRepository
import com.rootrecord.kilauea.alerts.domain.LiveFeedsCatalog
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class LiveFeedsUiState(
    val loading: Boolean = false,
    val catalog: LiveFeedsCatalog? = null,
    val error: String? = null,
)

@HiltViewModel
class LiveFeedsViewModel @Inject constructor(
    private val repo: LiveFeedsRepository,
    prefs: KilaueaPreferences,
) : ViewModel() {

    private val _state = MutableStateFlow(LiveFeedsUiState())
    val state = _state.asStateFlow()

    val newBadge = prefs.liveFeedsNewBadge.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5000),
        false,
    )

    init {
        refresh(false)
    }

    fun refresh(force: Boolean) {
        viewModelScope.launch {
            _state.update { it.copy(loading = true, error = null) }
            val r = repo.offlineFirst(force)
            _state.update {
                it.copy(
                    loading = false,
                    catalog = r.getOrNull(),
                    error = r.exceptionOrNull()?.message,
                )
            }
        }
    }

    fun visitedTab() {
        viewModelScope.launch {
            repo.clearNewBadge()
        }
    }
}
